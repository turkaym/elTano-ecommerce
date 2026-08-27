package com.eltano.ecommerce.procurement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import com.eltano.ecommerce.catalog.domain.Category;
import com.eltano.ecommerce.catalog.domain.InventoryPolicy;
import com.eltano.ecommerce.catalog.domain.Product;
import com.eltano.ecommerce.catalog.domain.ProductType;
import com.eltano.ecommerce.catalog.domain.ProductVariant;
import com.eltano.ecommerce.catalog.domain.UnitType;
import com.eltano.ecommerce.catalog.repository.CategoryRepository;
import com.eltano.ecommerce.catalog.repository.ProductRepository;
import com.eltano.ecommerce.catalog.repository.ProductVariantRepository;
import com.eltano.ecommerce.procurement.domain.DispositionType;
import com.eltano.ecommerce.procurement.domain.InventoryTargetType;
import com.eltano.ecommerce.procurement.domain.PurchaseStatus;
import com.eltano.ecommerce.procurement.repository.PurchaseReceiptRepository;
import com.eltano.ecommerce.procurement.repository.PurchaseRepository;
import com.eltano.ecommerce.procurement.repository.StockMovementRepository;
import com.eltano.ecommerce.procurement.service.ProcurementConflictException;
import com.eltano.ecommerce.procurement.service.ProcurementService;

@SpringBootTest
@ActiveProfiles("test")
class ReceiptConfirmationPostgreSqlIntegrationTest extends PostgreSqlIntegrationSupport {
    @Autowired ProcurementService procurement;
    @Autowired CategoryRepository categories;
    @Autowired ProductRepository products;
    @Autowired ProductVariantRepository variants;
    @Autowired PurchaseRepository purchases;
    @Autowired PurchaseReceiptRepository receipts;
    @Autowired StockMovementRepository movements;
    @Autowired TransactionTemplate transactions;

    UUID purchaseId;
    UUID purchaseLineId;
    UUID variantId;

    @BeforeEach
    void setUpPurchase() {
        transactions.executeWithoutResult(status -> {
            Category category = new Category();
            category.setName("Receipt " + UUID.randomUUID());
            category.setSlug("receipt-" + UUID.randomUUID());
            category.setActive(true);
            categories.save(category);
            Product product = new Product();
            product.setName("Receipt target");
            product.setSlug("receipt-target-" + UUID.randomUUID());
            product.setDescription("Receipt target");
            product.setActive(true);
            product.setCategory(category);
            product.setProductType(ProductType.ENVASADO);
            product.setInventoryPolicy(InventoryPolicy.PER_VARIANT);
            ProductVariant variant = new ProductVariant();
            variant.setSku("RECEIPT-" + UUID.randomUUID());
            variant.setUnitType(UnitType.UNIT);
            variant.setUnitLabel("unit");
            variant.setPrice(BigDecimal.ONE);
            variant.setStockAvailable(5);
            variant.setStockReserved(0);
            variant.setActive(true);
            product.addVariant(variant);
            products.saveAndFlush(product);
            variantId = variant.getId();

            var supplier = procurement.createSupplier(new ProcurementService.SupplierCommand("Concurrent supplier", null, true));
            var mapping = procurement.createMapping(new ProcurementService.MappingCommand(
                    supplier.id(), "ITEM-" + UUID.randomUUID(), "Concurrent item", InventoryTargetType.VARIANT_UNIT,
                    null, variantId, BigDecimal.ONE, true));
            var purchase = procurement.createPurchase(new ProcurementService.PurchaseCommand(
                    supplier.id(), "Invoice", "CONCURRENT-" + UUID.randomUUID(), LocalDate.now(),
                    List.of(new ProcurementService.PurchaseLineCommand(mapping.id(), BigDecimal.valueOf(2), BigDecimal.ONE))), "creator");
            purchaseId = purchase.id();
            purchaseLineId = purchase.lines().getFirst().id();
        });
    }

    @Test
    void concurrentPartialReceiptsSerializeOnPurchaseAndCommitExactlyOnceEach() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> confirmAfterBarrier(ready, start, "receipt-a", "actor-a", "corr-a"));
            var second = executor.submit(() -> confirmAfterBarrier(ready, start, "receipt-b", "actor-b", "corr-b"));
            ready.await(5, TimeUnit.SECONDS);
            start.countDown();
            assertFalse(first.get(Duration.ofSeconds(10).toMillis(), TimeUnit.MILLISECONDS).replayed());
            assertFalse(second.get(Duration.ofSeconds(10).toMillis(), TimeUnit.MILLISECONDS).replayed());
        }

        assertEquals(7, variants.findById(variantId).orElseThrow().getStockAvailable());
        assertEquals(PurchaseStatus.RECEIVED, purchases.findById(purchaseId).orElseThrow().getStatus());
        assertEquals(2, receipts.findAllByPurchaseIdOrderByConfirmedAt(purchaseId).size());
        assertEquals(2, movements.findAllByPurchaseIdOrderByCreatedAt(purchaseId).size());
    }

    @Test
    void blockedCorrectionRollsBackReceiptMovementAndBalanceOnPostgreSql() {
        procurement.confirm(purchaseId, receipt(BigDecimal.valueOf(2)), "initial", "receiver", "corr-initial");
        transactions.executeWithoutResult(status -> {
            ProductVariant variant = variants.findById(variantId).orElseThrow();
            variant.setStockReserved(7);
        });

        assertThrows(ProcurementConflictException.class, () -> procurement.correct(purchaseId,
                new ProcurementService.CorrectionCommand("Unsafe count correction", List.of(
                        new ProcurementService.CorrectionDelta(InventoryTargetType.VARIANT_UNIT, variantId, -8))),
                "blocked-correction", "reviewer", "corr-blocked"));

        assertEquals(7, variants.findById(variantId).orElseThrow().getStockAvailable());
        assertEquals(1, receipts.findAllByPurchaseIdOrderByConfirmedAt(purchaseId).size());
        assertEquals(1, movements.findAllByPurchaseIdOrderByCreatedAt(purchaseId).size());
    }

    @Test
    void cancellationPersistsOnlyAppliedAggregateMovementEvidence() {
        transactions.executeWithoutResult(status -> variants.findById(variantId).orElseThrow().setStockAvailable(0));
        var excessReceipt = new ProcurementService.ReceiptCommand(List.of(new ProcurementService.ReceiptLineCommand(
                purchaseLineId, List.of(new ProcurementService.DispositionCommand(
                        DispositionType.ACCEPTED_EXCESS, BigDecimal.TEN, "Unexpected overage")))));
        procurement.confirm(purchaseId, excessReceipt, "aggregate-receipt", "receiver", "corr-receipt");
        procurement.correct(purchaseId, new ProcurementService.CorrectionCommand("Count correction", List.of(
                new ProcurementService.CorrectionDelta(InventoryTargetType.VARIANT_UNIT, variantId, -5))),
                "aggregate-correction", "reviewer", "corr-correction");

        var cancellation = procurement.cancel(purchaseId, new ProcurementService.ReasonCommand("Order cancelled"),
                "aggregate-cancellation", "manager", "corr-cancellation");

        assertEquals(PurchaseStatus.CANCELLED, cancellation.status());
        assertEquals(1, cancellation.canonicalDeltas().size());
        var cancellationDelta = cancellation.canonicalDeltas().getFirst();
        assertEquals(-5, cancellationDelta.delta());
        assertEquals(5, cancellationDelta.quantity().intValueExact());
        assertEquals(1, cancellationDelta.conversion().intValueExact());

        var cancellationMovements = movements.findAllByReceiptIdOrderByCreatedAt(cancellation.receiptId());
        assertEquals(1, cancellationMovements.size());
        var cancellationMovement = cancellationMovements.getFirst();
        assertEquals(-5, cancellationMovement.getCanonicalDelta());
        assertEquals(5, cancellationMovement.getBeforeBalance());
        assertEquals(0, cancellationMovement.getAfterBalance());
        assertEquals(5, cancellationMovement.getQuantity().intValueExact());
        assertEquals(1, cancellationMovement.getConversion().intValueExact());

        var persistedMovements = movements.findAllByPurchaseIdOrderByCreatedAt(purchaseId);
        assertEquals(3, persistedMovements.size());
        assertTrue(persistedMovements.stream().allMatch(movement ->
                movement.getBeforeBalance() >= 0 && movement.getAfterBalance() >= 0));
        assertEquals(3, receipts.findAllByPurchaseIdOrderByConfirmedAt(purchaseId).size());
        assertEquals(0, variants.findById(variantId).orElseThrow().getStockAvailable());
    }

    private ProcurementService.ReceiptResponse confirmAfterBarrier(CountDownLatch ready, CountDownLatch start,
            String key, String actor, String correlation) throws InterruptedException {
        ready.countDown();
        start.await(5, TimeUnit.SECONDS);
        return procurement.confirm(purchaseId, receipt(BigDecimal.ONE), key, actor, correlation);
    }

    private ProcurementService.ReceiptCommand receipt(BigDecimal quantity) {
        return new ProcurementService.ReceiptCommand(List.of(new ProcurementService.ReceiptLineCommand(purchaseLineId,
                List.of(new ProcurementService.DispositionCommand(DispositionType.ACCEPTED_ORDERED, quantity, null)))));
    }
}
