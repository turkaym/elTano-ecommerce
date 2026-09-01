package com.eltano.ecommerce.procurement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.jdbc.core.JdbcTemplate;

import com.eltano.ecommerce.catalog.domain.Category;
import com.eltano.ecommerce.catalog.domain.InventoryPolicy;
import com.eltano.ecommerce.catalog.domain.Product;
import com.eltano.ecommerce.catalog.domain.ProductType;
import com.eltano.ecommerce.catalog.domain.ProductVariant;
import com.eltano.ecommerce.catalog.domain.UnitType;
import com.eltano.ecommerce.catalog.repository.CategoryRepository;
import com.eltano.ecommerce.catalog.repository.ProductRepository;
import com.eltano.ecommerce.catalog.repository.ProductVariantRepository;
import com.eltano.ecommerce.inventory.service.InventoryInvariantException;
import com.eltano.ecommerce.procurement.draft.domain.PurchaseDraftStatus;
import com.eltano.ecommerce.procurement.draft.domain.PurchaseDraftUnit;
import com.eltano.ecommerce.procurement.draft.repository.PurchaseDraftRepository;
import com.eltano.ecommerce.procurement.draft.service.PurchaseDraftException;
import com.eltano.ecommerce.procurement.draft.service.PurchaseDraftService;
import com.eltano.ecommerce.procurement.repository.PurchaseReceiptRepository;
import com.eltano.ecommerce.procurement.repository.PurchaseRepository;
import com.eltano.ecommerce.procurement.repository.StockMovementRepository;
import com.eltano.ecommerce.procurement.repository.SupplierItemMappingRepository;
import com.eltano.ecommerce.procurement.service.ProcurementService;
import com.eltano.ecommerce.procurement.service.ProcurementConflictException;

@SpringBootTest
@ActiveProfiles("test")
class PurchaseDraftConfirmationPostgreSqlIntegrationTest extends PostgreSqlIntegrationSupport {
    @Autowired PurchaseDraftService service;
    @Autowired PurchaseDraftRepository drafts;
    @Autowired ProcurementService procurement;
    @Autowired CategoryRepository categories;
    @Autowired ProductRepository products;
    @Autowired ProductVariantRepository variants;
    @Autowired PurchaseRepository purchases;
    @Autowired PurchaseReceiptRepository receipts;
    @Autowired StockMovementRepository movements;
    @Autowired SupplierItemMappingRepository mappings;
    @Autowired TransactionTemplate transactions;
    @Autowired JdbcTemplate jdbc;

    @Test
    void confirmationCreatesOneImmutablePurchaseReceiptMovementAndSupportsStableReplay() {
        Fixture fixture = fixture(5);
        ReadyDraft ready = readyDraft(fixture, "Cafe proveedor");
        long purchaseCount = purchases.count(); long receiptCount = receipts.count(); long movementCount = movements.count();

        var confirmed = service.confirm(ready.id(), new PurchaseDraftService.ConfirmCommand(ready.version(), ready.hash()),
                "confirm-one", "admin", "corr-one");
        assertFalse(confirmed.replayed());
        assertTrue(confirmed.receiptId() != null); assertEquals(1, confirmed.canonicalDeltas().size());
        assertEquals(purchaseCount + 1, purchases.count()); assertEquals(receiptCount + 1, receipts.count()); assertEquals(movementCount + 1, movements.count());
        assertEquals(7, variants.findById(fixture.variantId()).orElseThrow().getStockAvailable());
        var costedVariant = variants.findById(fixture.variantId()).orElseThrow();
        assertEquals(new BigDecimal("125.50"), costedVariant.getLatestUnitCost());
        assertEquals("UNIDAD", costedVariant.getLatestCostUnit());
        assertEquals(confirmed.receiptId(), costedVariant.getLatestCostReceiptId());
        var purchaseLine = procurement.getPurchase(confirmed.purchaseId()).lines().getFirst();
        assertEquals(new BigDecimal("125.50"), purchaseLine.unitPrice());
        assertEquals(new BigDecimal("251.00"), purchaseLine.lineTotal());

        var replay = service.confirm(ready.id(), new PurchaseDraftService.ConfirmCommand(ready.version(), ready.hash()),
                "confirm-one", "admin", "corr-replay");
        assertTrue(replay.replayed()); assertEquals(confirmed.purchaseId(), replay.purchaseId());
        assertEquals(confirmed.receiptId(), replay.receiptId()); assertEquals(confirmed.canonicalDeltas(), replay.canonicalDeltas());
        assertEquals(7, variants.findById(fixture.variantId()).orElseThrow().getStockAvailable());
        assertEquals(confirmed.receiptId(), variants.findById(fixture.variantId()).orElseThrow().getLatestCostReceiptId());
        assertThrows(PurchaseDraftException.class, () -> service.patch(ready.id(), new PurchaseDraftService.MetadataCommand(ready.version(), LocalDate.now())));
    }

    @Test
    void concurrentConfirmationSerializesAndMutatesInventoryOnce() throws Exception {
        Fixture fixture = fixture(10);
        ReadyDraft ready = readyDraft(fixture, "Concurrente");
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> { start.await(); return service.confirm(ready.id(), new PurchaseDraftService.ConfirmCommand(ready.version(), ready.hash()), "same-key", "admin", "corr-a"); });
            var second = executor.submit(() -> { start.await(); return service.confirm(ready.id(), new PurchaseDraftService.ConfirmCommand(ready.version(), ready.hash()), "same-key", "admin", "corr-b"); });
            start.countDown();
            var a = first.get(20, TimeUnit.SECONDS); var b = second.get(20, TimeUnit.SECONDS);
            assertTrue(a.replayed() ^ b.replayed());
        }
        assertEquals(12, variants.findById(fixture.variantId()).orElseThrow().getStockAvailable());
    }

    @Test
    void inventoryFailureRollsBackPurchaseEvidenceAndLeavesDraftMutable() {
        Fixture fixture = fixture(Integer.MAX_VALUE);
        ReadyDraft ready = readyDraft(fixture, "Overflow");
        long purchaseCount = purchases.count(); long receiptCount = receipts.count(); long movementCount = movements.count();
        assertThrows(InventoryInvariantException.class, () -> service.confirm(ready.id(),
                new PurchaseDraftService.ConfirmCommand(ready.version(), ready.hash()), "overflow-key", "admin", "corr-overflow"));
        assertEquals(purchaseCount, purchases.count()); assertEquals(receiptCount, receipts.count()); assertEquals(movementCount, movements.count());
        assertEquals(PurchaseDraftStatus.DRAFT, drafts.findById(ready.id()).orElseThrow().getStatus());
        assertEquals(null, variants.findById(fixture.variantId()).orElseThrow().getLatestUnitCost());
    }

    @Test
    void rejectsDifferentUnitCostsForTheSameTargetBeforeAnyEvidenceOrStockMutation() {
        Fixture fixture = fixture(4);
        var created = service.create(new PurchaseDraftService.ManualDraftCommand(fixture.supplierId(), LocalDate.of(2026, 8, 29), List.of(
                new PurchaseDraftService.LineCommand(0, "Cafe A", BigDecimal.ONE, PurchaseDraftUnit.UNIDAD, "100"),
                new PurchaseDraftService.LineCommand(0, "Cafe B", BigDecimal.valueOf(2), PurchaseDraftUnit.UNIDAD, "120"))), "admin");
        var first = service.match(created.id(), created.lines().get(0).id(), new PurchaseDraftService.MatchCommand(created.version(), fixture.variantId(), false));
        var second = service.match(created.id(), first.lines().get(1).id(), new PurchaseDraftService.MatchCommand(first.version(), fixture.variantId(), false));
        var preview = service.preview(created.id(), new PurchaseDraftService.VersionCommand(second.version()));
        long purchaseCount = purchases.count(); long receiptCount = receipts.count(); long movementCount = movements.count();

        ProcurementConflictException conflict = assertThrows(ProcurementConflictException.class, () -> service.confirm(created.id(),
                new PurchaseDraftService.ConfirmCommand(preview.version(), preview.previewHash()), "cost-conflict", "admin", "corr-conflict"));

        assertEquals("CONFLICTING_UNIT_COST", conflict.getCode());
        assertEquals(purchaseCount, purchases.count()); assertEquals(receiptCount, receipts.count()); assertEquals(movementCount, movements.count());
        assertEquals(4, variants.findById(fixture.variantId()).orElseThrow().getStockAvailable());
        assertEquals(null, variants.findById(fixture.variantId()).orElseThrow().getLatestUnitCost());
    }

    @Test
    void concurrentPurchasesKeepTheNewestReceiptCostEvidence() throws Exception {
        Fixture fixture = fixture(0);
        ReadyDraft firstDraft = readyDraft(fixture, "Costo concurrente A", "100");
        ReadyDraft secondDraft = readyDraft(fixture, "Costo concurrente B", "200");
        CountDownLatch start = new CountDownLatch(1);
        PurchaseDraftService.ConfirmResponse first;
        PurchaseDraftService.ConfirmResponse second;
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> { start.await(); return service.confirm(firstDraft.id(), new PurchaseDraftService.ConfirmCommand(firstDraft.version(), firstDraft.hash()), "cost-a", "admin", "corr-a"); });
            var b = executor.submit(() -> { start.await(); return service.confirm(secondDraft.id(), new PurchaseDraftService.ConfirmCommand(secondDraft.version(), secondDraft.hash()), "cost-b", "admin", "corr-b"); });
            start.countDown(); first = a.get(20, TimeUnit.SECONDS); second = b.get(20, TimeUnit.SECONDS);
        }
        var firstReceipt = receipts.findById(first.receiptId()).orElseThrow();
        var secondReceipt = receipts.findById(second.receiptId()).orElseThrow();
        var newest = java.util.Comparator.comparing(com.eltano.ecommerce.procurement.domain.PurchaseReceipt::getConfirmedAt)
                .thenComparing(receipt -> receipt.getId().toString()).compare(firstReceipt, secondReceipt) >= 0 ? firstReceipt : secondReceipt;
        BigDecimal expected = newest.getId().equals(first.receiptId()) ? new BigDecimal("100.00") : new BigDecimal("200.00");
        var target = variants.findById(fixture.variantId()).orElseThrow();
        assertEquals(newest.getId(), target.getLatestCostReceiptId());
        assertEquals(expected, target.getLatestUnitCost());
        assertEquals(4, target.getStockAvailable());
    }

    @Test
    void bulkCostUsesDeclaredKilogramsInsteadOfCanonicalGrams() {
        BulkFixture fixture = bulkFixture();
        var created = service.create(new PurchaseDraftService.ManualDraftCommand(fixture.supplierId(), LocalDate.of(2026, 8, 29), List.of(
                new PurchaseDraftService.LineCommand(0, "Cafe granel", new BigDecimal("1.125"), PurchaseDraftUnit.KG, "200"))), "admin");
        var matched = service.match(created.id(), created.lines().getFirst().id(), new PurchaseDraftService.MatchCommand(created.version(), fixture.productId(), false));
        var preview = service.preview(created.id(), new PurchaseDraftService.VersionCommand(matched.version()));
        var confirmed = service.confirm(created.id(), new PurchaseDraftService.ConfirmCommand(preview.version(), preview.previewHash()), "bulk-cost", "admin", "bulk-corr");

        var product = products.findById(fixture.productId()).orElseThrow();
        assertEquals(1125, product.getStockBaseGrams());
        assertEquals(new BigDecimal("200.00"), product.getLatestUnitCost());
        assertEquals("KG", product.getLatestCostUnit());
        assertEquals(confirmed.receiptId(), product.getLatestCostReceiptId());
        var line = procurement.getPurchase(confirmed.purchaseId()).lines().getFirst();
        assertEquals(new BigDecimal("225.00"), line.lineTotal());
    }

    @Test
    void cancellingLatestPurchaseRestoresPriorVariantCost() {
        Fixture fixture = fixture(0);
        ReadyDraft priorDraft = readyDraft(fixture, "Costo anterior", "100");
        var prior = service.confirm(priorDraft.id(), new PurchaseDraftService.ConfirmCommand(priorDraft.version(), priorDraft.hash()), "prior-cost", "admin", "prior");
        ReadyDraft latestDraft = readyDraft(fixture, "Costo reciente", "200");
        var latest = service.confirm(latestDraft.id(), new PurchaseDraftService.ConfirmCommand(latestDraft.version(), latestDraft.hash()), "latest-cost", "admin", "latest");

        procurement.cancel(latest.purchaseId(), new ProcurementService.ReasonCommand("Cancelar reciente"), "cancel-latest", "admin", "cancel-latest");

        var target = variants.findById(fixture.variantId()).orElseThrow();
        assertEquals(new BigDecimal("100.00"), target.getLatestUnitCost());
        assertEquals(prior.receiptId(), target.getLatestCostReceiptId());
    }

    @Test
    void cancellingOnlyBulkCostClearsLatestEvidence() {
        BulkFixture fixture = bulkFixture();
        var created = service.create(new PurchaseDraftService.ManualDraftCommand(fixture.supplierId(), LocalDate.of(2026, 8, 29), List.of(
                new PurchaseDraftService.LineCommand(0, "Costo granel unico", BigDecimal.ONE, PurchaseDraftUnit.KG, "300"))), "admin");
        var matched = service.match(created.id(), created.lines().getFirst().id(), new PurchaseDraftService.MatchCommand(created.version(), fixture.productId(), false));
        var preview = service.preview(created.id(), new PurchaseDraftService.VersionCommand(matched.version()));
        var confirmed = service.confirm(created.id(), new PurchaseDraftService.ConfirmCommand(preview.version(), preview.previewHash()), "only-bulk", "admin", "only-bulk");

        procurement.cancel(confirmed.purchaseId(), new ProcurementService.ReasonCommand("Cancelar unica"), "cancel-only", "admin", "cancel-only");

        var target = products.findById(fixture.productId()).orElseThrow();
        assertEquals(null, target.getLatestUnitCost()); assertEquals(null, target.getLatestCostUnit());
        assertEquals(null, target.getLatestCostAt()); assertEquals(null, target.getLatestCostPurchaseLineId());
        assertEquals(null, target.getLatestCostReceiptId());
    }

    @Test
    void cancellingOlderPurchasePreservesNewerVariantCost() {
        Fixture fixture = fixture(0);
        ReadyDraft oldDraft = readyDraft(fixture, "Costo viejo", "100");
        var old = service.confirm(oldDraft.id(), new PurchaseDraftService.ConfirmCommand(oldDraft.version(), oldDraft.hash()), "old-cost", "admin", "old");
        ReadyDraft newDraft = readyDraft(fixture, "Costo nuevo", "250");
        var newest = service.confirm(newDraft.id(), new PurchaseDraftService.ConfirmCommand(newDraft.version(), newDraft.hash()), "new-cost", "admin", "new");

        procurement.cancel(old.purchaseId(), new ProcurementService.ReasonCommand("Cancelar anterior"), "cancel-old", "admin", "cancel-old");

        var target = variants.findById(fixture.variantId()).orElseThrow();
        assertEquals(new BigDecimal("250.00"), target.getLatestUnitCost());
        assertEquals(newest.receiptId(), target.getLatestCostReceiptId());
    }

    @Test
    void blockedCancellationPreservesCurrentLatestCost() {
        Fixture fixture = fixture(0);
        ReadyDraft ready = readyDraft(fixture, "Costo bloqueado", "175");
        var confirmed = service.confirm(ready.id(), new PurchaseDraftService.ConfirmCommand(ready.version(), ready.hash()), "blocked-cost", "admin", "blocked");
        jdbc.update("update product_variants set stock_available=0 where id=?", fixture.variantId());

        ProcurementConflictException blocked = assertThrows(ProcurementConflictException.class, () -> procurement.cancel(confirmed.purchaseId(),
                new ProcurementService.ReasonCommand("Sin stock para revertir"), "blocked-cancel", "admin", "blocked-cancel"));

        assertEquals("REVERSAL_BLOCKED", blocked.getCode());
        var target = variants.findById(fixture.variantId()).orElseThrow();
        assertEquals(new BigDecimal("175.00"), target.getLatestUnitCost());
        assertEquals(confirmed.receiptId(), target.getLatestCostReceiptId());
    }

    @Test
    void duplicateRowsAndUnresolvedProductsBlockPreviewAndMutationInvalidatesIt() {
        Fixture fixture = fixture(0);
        var duplicate = service.create(new PurchaseDraftService.ManualDraftCommand(fixture.supplierId(), LocalDate.now(), List.of(
                new PurchaseDraftService.LineCommand(0, "Cafe", BigDecimal.ONE, PurchaseDraftUnit.UNIDAD, "100"),
                new PurchaseDraftService.LineCommand(0, "CAFE", BigDecimal.ONE, PurchaseDraftUnit.UNIDAD, "100"))), "admin");
        assertTrue(duplicate.lines().stream().allMatch(line -> line.errors().stream().anyMatch(error -> error.contains("duplicada"))));
        var blocked = service.preview(duplicate.id(), new PurchaseDraftService.VersionCommand(duplicate.version()));
        assertFalse(blocked.ready());

        ReadyDraft ready = readyDraft(fixture, "Editable");
        var current = service.get(ready.id());
        var changed = service.patchLine(ready.id(), current.lines().getFirst().id(),
                new PurchaseDraftService.LineCommand(current.version(), "Editable", BigDecimal.valueOf(2), PurchaseDraftUnit.UNIDAD, "130.00"));
        assertEquals(null, changed.previewHash());
        assertEquals(new BigDecimal("130.00"), changed.lines().getFirst().unitPrice());
        assertEquals("UNRESOLVED", changed.lines().getFirst().matchStatus().name());
        PurchaseDraftException stale = assertThrows(PurchaseDraftException.class, () -> service.confirm(ready.id(),
                new PurchaseDraftService.ConfirmCommand(changed.version(), ready.hash()), "stale-price", "admin", "corr-stale"));
        assertEquals("PREVIEW_STALE", stale.getCode());
        assertThrows(PurchaseDraftException.class, () -> service.patch(ready.id(), new PurchaseDraftService.MetadataCommand(current.version(), LocalDate.now())));
    }

    @Test
    void legacyMatchedDraftWithNullCostsRemainsReadableAndPreviewIsRejected() {
        Fixture fixture = fixture(0);
        ReadyDraft legacy = readyDraft(fixture, "Legacy sin costo");
        jdbc.update("update purchase_draft_lines set unit_price=null,line_total=null,pricing_unit=null,currency=null where draft_id=?", legacy.id());

        var readable = service.get(legacy.id());
        assertEquals(null, readable.lines().getFirst().unitPrice());
        assertTrue(readable.lines().getFirst().errors().stream().anyMatch(error -> error.contains("precio unitario")));
        var preview = service.preview(legacy.id(), new PurchaseDraftService.VersionCommand(legacy.version()));
        assertFalse(preview.ready());
        assertEquals(null, preview.previewHash());
        assertTrue(preview.errors().stream().anyMatch(error -> "UNIT_COST_REQUIRED".equals(error.code())));
    }

    @Test
    void legacyConfirmedDraftWithNullCostsReplaysWithoutPreviewRecalculation() {
        Fixture fixture = fixture(0);
        ReadyDraft donor = readyDraft(fixture, "Donante confirmado");
        var evidence = service.confirm(donor.id(), new PurchaseDraftService.ConfirmCommand(donor.version(), donor.hash()),
                "donor-key", "admin", "donor-correlation");
        ReadyDraft legacy = readyDraft(fixture, "Legacy confirmado");
        String previewHash = "legacy-preview";
        String requestHash = sha256(legacy.version() + "|" + previewHash);
        jdbc.update("update purchase_draft_lines set unit_price=null,line_total=null,pricing_unit=null,currency=null where draft_id=?", legacy.id());
        jdbc.update("update purchase_drafts set status='CONFIRMED',confirmed_purchase_id=?,confirmed_receipt_id=?,confirm_idempotency_key=?,confirm_request_hash=? where id=?",
                evidence.purchaseId(), evidence.receiptId(), "legacy-key", requestHash, legacy.id());

        var replay = service.confirm(legacy.id(), new PurchaseDraftService.ConfirmCommand(legacy.version(), previewHash),
                "legacy-key", "admin", "legacy-replay");
        assertTrue(replay.replayed());
        assertEquals(evidence.purchaseId(), replay.purchaseId());
        assertEquals(null, replay.canonicalDeltas().getFirst().unitPrice());
        PurchaseDraftException differentKey = assertThrows(PurchaseDraftException.class, () -> service.confirm(legacy.id(),
                new PurchaseDraftService.ConfirmCommand(legacy.version(), previewHash), "different-key", "admin", "legacy-conflict"));
        assertEquals("IDEMPOTENCY_CONFLICT", differentKey.getCode());
        PurchaseDraftException differentContent = assertThrows(PurchaseDraftException.class, () -> service.confirm(legacy.id(),
                new PurchaseDraftService.ConfirmCommand(legacy.version(), "different-preview"), "legacy-key", "admin", "legacy-conflict"));
        assertEquals("IDEMPOTENCY_CONFLICT", differentContent.getCode());
    }

    @Test
    void canonicalDuplicateDatesSurvivePersistenceRefresh() {
        Fixture fixture = fixture(0);
        var imported = service.importWorkbook(fixture.supplierId(), multipart("dates.xlsx", mixedDateWorkbookBytes()), "dates-key", "admin");
        var refreshed = service.get(imported.id());
        assertTrue(refreshed.lines().stream().map(PurchaseDraftService.LineResponse::sourceDate).distinct().count() > 1);
        assertTrue(refreshed.lines().stream().allMatch(line -> line.errors().stream().anyMatch(error -> error.contains("duplicada"))));
    }

    @Test
    void confirmedSupplierFileHashIsBlockedAndLinksExistingPurchase() {
        Fixture fixture = fixture(0);
        byte[] content = workbookBytes("Archivo unico", "2", "unidad");
        var imported = service.importWorkbook(fixture.supplierId(), multipart("original.xlsx", content), "upload-one", "admin");
        var matched = service.match(imported.id(), imported.lines().getFirst().id(),
                new PurchaseDraftService.MatchCommand(imported.version(), fixture.variantId(), false));
        var preview = service.preview(imported.id(), new PurchaseDraftService.VersionCommand(matched.version()));
        var confirmed = service.confirm(imported.id(), new PurchaseDraftService.ConfirmCommand(preview.version(), preview.previewHash()),
                "confirm-file", "admin", "corr-file");
        assertTrue(service.sourceFile(imported.id()).resource().exists());
        PurchaseDraftException duplicate = assertThrows(PurchaseDraftException.class, () -> service.importWorkbook(
                fixture.supplierId(), multipart("renamed.xlsx", content), "upload-two", "admin"));
        assertEquals("DUPLICATE_CONFIRMED_FILE", duplicate.getCode());
        assertTrue(duplicate.getMessage().contains(confirmed.purchaseId().toString()));
    }

    @Test
    void concurrentSameSupplierUploadReusesOneDraft() throws Exception {
        Fixture fixture = fixture(0);
        byte[] content = workbookBytes("Carga concurrente", "1", "unidad");
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> { start.await(); return service.importWorkbook(fixture.supplierId(), multipart("a.xlsx", content), "upload-a", "admin"); });
            var second = executor.submit(() -> { start.await(); return service.importWorkbook(fixture.supplierId(), multipart("b.xlsx", content), "upload-b", "admin"); });
            start.countDown();
            var a = first.get(20, TimeUnit.SECONDS); var b = second.get(20, TimeUnit.SECONDS);
            assertEquals(a.id(), b.id()); assertTrue(a.reused() ^ b.reused());
        }
    }

    @Test
    void rememberedNameMappingIsSupplierScopedAndUnitPolicyIsEnforced() {
        Fixture fixture = fixture(0);
        ReadyDraft ready = readyDraft(fixture, "Cafe Premium");
        var otherSupplier = procurement.createSupplier(new ProcurementService.SupplierCommand("Otro " + UUID.randomUUID(), null, true));
        var remembered = importWorkbook(fixture.supplierId(), "CAFE---premium", "1", "unidad", "remembered");
        assertEquals("MATCHED", remembered.lines().getFirst().matchStatus().name());
        var other = importWorkbook(otherSupplier.id(), "CAFE---premium", "1", "unidad", "scoped");
        assertEquals("UNRESOLVED", other.lines().getFirst().matchStatus().name());

        var incompatible = importWorkbook(fixture.supplierId(), "Cafe Premium", "2", "kg", "unit-policy");
        assertEquals("UNRESOLVED", incompatible.lines().getFirst().matchStatus().name());
        var mapping = mappings.findBySupplierIdAndNormalizedNameAndActiveTrue(fixture.supplierId(), "cafe premium").orElseThrow();
        mapping.setActive(false); mappings.saveAndFlush(mapping);
        var inactive = importWorkbook(fixture.supplierId(), "Cafe Premium", "3", "unidad", "inactive");
        assertEquals("UNRESOLVED", inactive.lines().getFirst().matchStatus().name());
        var repaired = service.match(inactive.id(), inactive.lines().getFirst().id(),
                new PurchaseDraftService.MatchCommand(inactive.version(), fixture.otherVariantId(), true));
        assertEquals(fixture.otherVariantId(), repaired.lines().getFirst().variantId());
        assertTrue(mappings.findBySupplierIdAndNormalizedName(fixture.supplierId(), "cafe premium").orElseThrow().isActive());
        var activeConflict = service.create(new PurchaseDraftService.ManualDraftCommand(fixture.supplierId(), LocalDate.now(), List.of(
                new PurchaseDraftService.LineCommand(0, "Cafe Premium", BigDecimal.ONE, PurchaseDraftUnit.UNIDAD, "100"))), "admin");
        PurchaseDraftException conflict = assertThrows(PurchaseDraftException.class, () -> service.match(activeConflict.id(), activeConflict.lines().getFirst().id(),
                new PurchaseDraftService.MatchCommand(activeConflict.version(), fixture.variantId(), true)));
        assertEquals("MAPPING_CONFLICT", conflict.getCode());
        assertEquals(fixture.otherVariantId(), mappings.findBySupplierIdAndNormalizedName(fixture.supplierId(), "cafe premium").orElseThrow().getVariantId());
        assertTrue(ready.hash() != null);
    }

    @Test
    void explicitMappingRepairLeavesConfirmedDraftPurchaseReceiptAndMovementHistoryUntouched() {
        Fixture fixture = fixture(0);
        ReadyDraft ready = readyDraft(fixture, "Historia inmutable");
        var confirmed = service.confirm(ready.id(), new PurchaseDraftService.ConfirmCommand(ready.version(), ready.hash()),
                "history-confirm", "admin", "history-correlation");
        var mapping = mappings.findBySupplierIdAndNormalizedName(fixture.supplierId(), "historia inmutable").orElseThrow();
        long receiptCount = receipts.count(); long movementCount = movements.count();

        procurement.repairMapping(mapping.getId(), new ProcurementService.MappingRepairCommand(
                com.eltano.ecommerce.procurement.domain.InventoryTargetType.VARIANT_UNIT, null, fixture.otherVariantId(), BigDecimal.ONE, true));

        assertEquals(fixture.variantId(), service.get(ready.id()).lines().getFirst().variantId());
        assertEquals(fixture.variantId(), procurement.getPurchase(confirmed.purchaseId()).lines().getFirst().variantId());
        assertEquals(fixture.variantId(), movements.findAllByPurchaseIdOrderByCreatedAt(confirmed.purchaseId()).getFirst().getTargetId());
        assertEquals(receiptCount, receipts.count()); assertEquals(movementCount, movements.count());
        assertEquals(confirmed.receiptId(), service.get(ready.id()).confirmedReceiptId());
    }

    private PurchaseDraftService.DraftResponse importWorkbook(UUID supplierId, String name, String quantity, String unit, String key) {
        return service.importWorkbook(supplierId, multipart(key + ".xlsx", workbookBytes(name, quantity, unit)), key, "admin");
    }

    private byte[] workbookBytes(String name, String quantity, String unit) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Compra"); var header = sheet.createRow(0);
            String[] headers = {"fecha", "producto", "cantidad", "unidad", "precio_unitario"};
            for (int index = 0; index < headers.length; index++) header.createCell(index).setCellValue(headers[index]);
            var row = sheet.createRow(1); row.createCell(0).setCellValue("2026-08-29"); row.createCell(1).setCellValue(name);
            row.createCell(2).setCellValue(quantity); row.createCell(3).setCellValue(unit); row.createCell(4).setCellValue("100"); workbook.write(output); return output.toByteArray();
        } catch (java.io.IOException exception) { throw new IllegalStateException(exception); }
    }
    private byte[] mixedDateWorkbookBytes() {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Compra"); var header = sheet.createRow(0);
            String[] headers = {"fecha", "producto", "cantidad", "unidad", "precio_unitario"};
            for (int index = 0; index < headers.length; index++) header.createCell(index).setCellValue(headers[index]);
            var style = workbook.createCellStyle(); style.setDataFormat(workbook.createDataFormat().getFormat("yyyy-mm-dd"));
            for (int index = 1; index <= 3; index++) {
                var row = sheet.createRow(index); var date = row.createCell(0);
                if (index == 1) { date.setCellValue(LocalDate.of(2026, 8, 29)); date.setCellStyle(style); }
                else date.setCellValue(index == 2 ? "2026-08-29" : "29/8/2026");
                row.createCell(1).setCellValue("Cafe"); row.createCell(2).setCellValue("1"); row.createCell(3).setCellValue("unidad"); row.createCell(4).setCellValue("100");
            }
            workbook.write(output); return output.toByteArray();
        } catch (java.io.IOException exception) { throw new IllegalStateException(exception); }
    }
    private MockMultipartFile multipart(String name, byte[] content) { return new MockMultipartFile("file", name,
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", content); }

    private String sha256(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }

    private ReadyDraft readyDraft(Fixture fixture, String name) {
        return readyDraft(fixture, name, "125.50");
    }

    private ReadyDraft readyDraft(Fixture fixture, String name, String unitPrice) {
        var created = service.create(new PurchaseDraftService.ManualDraftCommand(fixture.supplierId(), LocalDate.of(2026, 8, 29), List.of(
                new PurchaseDraftService.LineCommand(0, name, BigDecimal.valueOf(2), PurchaseDraftUnit.UNIDAD, unitPrice))), "admin");
        var matched = service.match(created.id(), created.lines().getFirst().id(),
                new PurchaseDraftService.MatchCommand(created.version(), fixture.variantId(), true));
        var preview = service.preview(created.id(), new PurchaseDraftService.VersionCommand(matched.version()));
        assertTrue(preview.ready());
        return new ReadyDraft(created.id(), preview.version(), preview.previewHash());
    }

    private Fixture fixture(int stock) {
        return transactions.execute(status -> {
            Category category = new Category(); category.setName("Draft " + UUID.randomUUID()); category.setSlug("draft-" + UUID.randomUUID()); category.setActive(true); categories.save(category);
            Product product = new Product(); product.setName("Target"); product.setSlug("target-" + UUID.randomUUID()); product.setDescription("Target");
            product.setActive(true); product.setCategory(category); product.setProductType(ProductType.ENVASADO); product.setInventoryPolicy(InventoryPolicy.PER_VARIANT);
            ProductVariant variant = new ProductVariant(); variant.setSku("DRAFT-" + UUID.randomUUID()); variant.setUnitType(UnitType.UNIT); variant.setUnitLabel("unidad");
            variant.setPrice(BigDecimal.ONE); variant.setStockAvailable(stock); variant.setStockReserved(0); variant.setActive(true); product.addVariant(variant);
            ProductVariant other = new ProductVariant(); other.setSku("DRAFT-OTHER-" + UUID.randomUUID()); other.setUnitType(UnitType.UNIT); other.setUnitLabel("unidad");
            other.setPrice(BigDecimal.ONE); other.setStockAvailable(0); other.setStockReserved(0); other.setActive(true); product.addVariant(other); products.saveAndFlush(product);
            var supplier = procurement.createSupplier(new ProcurementService.SupplierCommand("Proveedor " + UUID.randomUUID(), null, true));
            return new Fixture(supplier.id(), variant.getId(), other.getId());
        });
    }

    private BulkFixture bulkFixture() {
        return transactions.execute(status -> {
            Category category = new Category(); category.setName("Bulk " + UUID.randomUUID()); category.setSlug("bulk-" + UUID.randomUUID()); category.setActive(true); categories.save(category);
            Product product = new Product(); product.setName("Bulk target"); product.setSlug("bulk-target-" + UUID.randomUUID()); product.setDescription("Bulk target");
            product.setActive(true); product.setCategory(category); product.setProductType(ProductType.GRANEL); product.setInventoryPolicy(InventoryPolicy.BULK_WEIGHT);
            product.setStockBaseGrams(0); product.setStockReservedBaseGrams(0); products.saveAndFlush(product);
            var supplier = procurement.createSupplier(new ProcurementService.SupplierCommand("Proveedor bulk " + UUID.randomUUID(), null, true));
            return new BulkFixture(supplier.id(), product.getId());
        });
    }

    private record Fixture(UUID supplierId, UUID variantId, UUID otherVariantId) { }
    private record BulkFixture(UUID supplierId, UUID productId) { }
    private record ReadyDraft(UUID id, long version, String hash) { }
}
