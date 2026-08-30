package com.eltano.ecommerce.procurement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
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

        var replay = service.confirm(ready.id(), new PurchaseDraftService.ConfirmCommand(ready.version(), ready.hash()),
                "confirm-one", "admin", "corr-replay");
        assertTrue(replay.replayed()); assertEquals(confirmed.purchaseId(), replay.purchaseId());
        assertEquals(confirmed.receiptId(), replay.receiptId()); assertEquals(confirmed.canonicalDeltas(), replay.canonicalDeltas());
        assertEquals(7, variants.findById(fixture.variantId()).orElseThrow().getStockAvailable());
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
    }

    @Test
    void duplicateRowsAndUnresolvedProductsBlockPreviewAndMutationInvalidatesIt() {
        Fixture fixture = fixture(0);
        var duplicate = service.create(new PurchaseDraftService.ManualDraftCommand(fixture.supplierId(), LocalDate.now(), List.of(
                new PurchaseDraftService.LineCommand(0, "Cafe", BigDecimal.ONE, PurchaseDraftUnit.UNIDAD),
                new PurchaseDraftService.LineCommand(0, "CAFE", BigDecimal.ONE, PurchaseDraftUnit.UNIDAD))), "admin");
        assertTrue(duplicate.lines().stream().allMatch(line -> line.errors().stream().anyMatch(error -> error.contains("duplicada"))));
        var blocked = service.preview(duplicate.id(), new PurchaseDraftService.VersionCommand(duplicate.version()));
        assertFalse(blocked.ready());

        ReadyDraft ready = readyDraft(fixture, "Editable");
        var current = service.get(ready.id());
        var changed = service.patchLine(ready.id(), current.lines().getFirst().id(),
                new PurchaseDraftService.LineCommand(current.version(), "Editable", BigDecimal.valueOf(3), PurchaseDraftUnit.UNIDAD));
        assertEquals(null, changed.previewHash());
        assertEquals("UNRESOLVED", changed.lines().getFirst().matchStatus().name());
        assertThrows(PurchaseDraftException.class, () -> service.patch(ready.id(), new PurchaseDraftService.MetadataCommand(current.version(), LocalDate.now())));
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
        assertTrue(ready.hash() != null);
    }

    private PurchaseDraftService.DraftResponse importWorkbook(UUID supplierId, String name, String quantity, String unit, String key) {
        return service.importWorkbook(supplierId, multipart(key + ".xlsx", workbookBytes(name, quantity, unit)), key, "admin");
    }

    private byte[] workbookBytes(String name, String quantity, String unit) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Compra"); var header = sheet.createRow(0);
            String[] headers = {"fecha", "producto", "cantidad", "unidad"};
            for (int index = 0; index < headers.length; index++) header.createCell(index).setCellValue(headers[index]);
            var row = sheet.createRow(1); row.createCell(0).setCellValue("2026-08-29"); row.createCell(1).setCellValue(name);
            row.createCell(2).setCellValue(quantity); row.createCell(3).setCellValue(unit); workbook.write(output); return output.toByteArray();
        } catch (java.io.IOException exception) { throw new IllegalStateException(exception); }
    }
    private byte[] mixedDateWorkbookBytes() {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("Compra"); var header = sheet.createRow(0);
            String[] headers = {"fecha", "producto", "cantidad", "unidad"};
            for (int index = 0; index < headers.length; index++) header.createCell(index).setCellValue(headers[index]);
            var style = workbook.createCellStyle(); style.setDataFormat(workbook.createDataFormat().getFormat("yyyy-mm-dd"));
            for (int index = 1; index <= 3; index++) {
                var row = sheet.createRow(index); var date = row.createCell(0);
                if (index == 1) { date.setCellValue(LocalDate.of(2026, 8, 29)); date.setCellStyle(style); }
                else date.setCellValue(index == 2 ? "2026-08-29" : "29/8/2026");
                row.createCell(1).setCellValue("Cafe"); row.createCell(2).setCellValue("1"); row.createCell(3).setCellValue("unidad");
            }
            workbook.write(output); return output.toByteArray();
        } catch (java.io.IOException exception) { throw new IllegalStateException(exception); }
    }
    private MockMultipartFile multipart(String name, byte[] content) { return new MockMultipartFile("file", name,
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", content); }

    private ReadyDraft readyDraft(Fixture fixture, String name) {
        var created = service.create(new PurchaseDraftService.ManualDraftCommand(fixture.supplierId(), LocalDate.of(2026, 8, 29), List.of(
                new PurchaseDraftService.LineCommand(0, name, BigDecimal.valueOf(2), PurchaseDraftUnit.UNIDAD))), "admin");
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
            variant.setPrice(BigDecimal.ONE); variant.setStockAvailable(stock); variant.setStockReserved(0); variant.setActive(true); product.addVariant(variant); products.saveAndFlush(product);
            var supplier = procurement.createSupplier(new ProcurementService.SupplierCommand("Proveedor " + UUID.randomUUID(), null, true));
            return new Fixture(supplier.id(), variant.getId());
        });
    }

    private record Fixture(UUID supplierId, UUID variantId) { }
    private record ReadyDraft(UUID id, long version, String hash) { }
}
