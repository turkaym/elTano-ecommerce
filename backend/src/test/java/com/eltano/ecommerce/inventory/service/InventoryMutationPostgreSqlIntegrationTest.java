package com.eltano.ecommerce.inventory.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
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
import com.eltano.ecommerce.catalog.api.dto.AdminProductUpsertRequest;
import com.eltano.ecommerce.catalog.api.dto.AdminProductVariantUpsertRequest;
import com.eltano.ecommerce.catalog.service.AdminProductService;
import com.eltano.ecommerce.procurement.domain.InventoryTargetType;
import com.eltano.ecommerce.procurement.PostgreSqlIntegrationSupport;
import com.eltano.ecommerce.procurement.repository.StockMovementRepository;
import com.eltano.ecommerce.procurement.service.ProcurementConflictException;
import com.eltano.ecommerce.orders.service.InventoryPolicyService;

@SpringBootTest
@ActiveProfiles("test")
class InventoryMutationPostgreSqlIntegrationTest extends PostgreSqlIntegrationSupport {
    @Autowired InventoryMutationService inventory;
    @Autowired CategoryRepository categories;
    @Autowired ProductRepository products;
    @Autowired ProductVariantRepository variants;
    @Autowired TransactionTemplate transactions;
    @Autowired InventoryPolicyService orderInventory;
    @Autowired AdminProductService adminProducts;
    @MockBean StockMovementRepository movements;

    UUID categoryId;
    UUID productId;
    UUID variantId;

    @BeforeEach
    void setUpTargets() {
        transactions.executeWithoutResult(status -> {
            Category category = new Category();
            category.setName("Inventory " + UUID.randomUUID());
            category.setSlug("inventory-" + UUID.randomUUID());
            category.setActive(true);
            categories.save(category);
            categoryId = category.getId();
            Product product = new Product();
            product.setName("Bulk target");
            product.setSlug("bulk-" + UUID.randomUUID());
            product.setDescription("Bulk target");
            product.setActive(true);
            product.setCategory(category);
            product.setProductType(ProductType.GRANEL);
            product.setInventoryPolicy(InventoryPolicy.BULK_WEIGHT);
            product.setStockBaseGrams(1000);
            product.setStockReservedBaseGrams(200);
            ProductVariant variant = new ProductVariant();
            variant.setSku("INV-" + UUID.randomUUID());
            variant.setUnitType(UnitType.UNIT);
            variant.setUnitLabel("unit");
            variant.setWeightGrams(100);
            variant.setPrice(BigDecimal.ONE);
            variant.setStockAvailable(5);
            variant.setStockReserved(2);
            variant.setActive(true);
            product.addVariant(variant);
            products.saveAndFlush(product);
            productId = product.getId();
            variantId = variant.getId();
        });
    }

    @Test
    void concurrentOppositeRequestsCompleteWithDeterministicProductThenVariantLocks() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                mutateAfterBarrier(ready, start, List.of(InventoryDelta.variant(variantId, 1), InventoryDelta.bulk(productId, 10)));
                return null;
            });
            var second = executor.submit(() -> {
                mutateAfterBarrier(ready, start, List.of(InventoryDelta.bulk(productId, 20), InventoryDelta.variant(variantId, 1)));
                return null;
            });
            ready.await(5, TimeUnit.SECONDS);
            start.countDown();
            first.get(Duration.ofSeconds(10).toMillis(), TimeUnit.MILLISECONDS);
            second.get(Duration.ofSeconds(10).toMillis(), TimeUnit.MILLISECONDS);
        }

        assertEquals(1030, products.findById(productId).orElseThrow().getStockBaseGrams());
        assertEquals(7, variants.findById(variantId).orElseThrow().getStockAvailable());
        assertEquals(200, products.findById(productId).orElseThrow().getStockReservedBaseGrams());
        assertEquals(2, variants.findById(variantId).orElseThrow().getStockReserved());
    }

    @Test
    void mixedTargetInvariantFailureRollsBackEveryBalance() {
        assertThrows(InventoryInvariantException.class, () -> transactions.executeWithoutResult(status -> inventory.apply(List.of(
                InventoryDelta.bulk(productId, 500), InventoryDelta.variant(variantId, -4)))));

        assertEquals(1000, products.findById(productId).orElseThrow().getStockBaseGrams());
        assertEquals(5, variants.findById(variantId).orElseThrow().getStockAvailable());
    }

    @Test
    void orderReserveReleaseAndFinalizeUseNeutralBoundaryWithoutChangingLegacySemantics() {
        transactions.executeWithoutResult(status -> orderInventory.reserve(variants.findById(variantId).orElseThrow(), 2));
        assertEquals(1000, products.findById(productId).orElseThrow().getStockBaseGrams());
        assertEquals(400, products.findById(productId).orElseThrow().getStockReservedBaseGrams());

        transactions.executeWithoutResult(status -> orderInventory.release(variants.findById(variantId).orElseThrow(), 1));
        assertEquals(1000, products.findById(productId).orElseThrow().getStockBaseGrams());
        assertEquals(300, products.findById(productId).orElseThrow().getStockReservedBaseGrams());

        transactions.executeWithoutResult(status -> orderInventory.finalizeReservation(variants.findById(variantId).orElseThrow(), 1));
        assertEquals(900, products.findById(productId).orElseThrow().getStockBaseGrams());
        assertEquals(200, products.findById(productId).orElseThrow().getStockReservedBaseGrams());
        assertEquals(5, variants.findById(variantId).orElseThrow().getStockAvailable());
        assertEquals(2, variants.findById(variantId).orElseThrow().getStockReserved());
    }

    @Test
    void adminStockUpdateWaitsForLedgerMutationAndRejectsStaleBalance() throws Exception {
        when(movements.existsByTargetTypeAndTargetId(InventoryTargetType.BULK_GRAM, productId)).thenReturn(true);
        CountDownLatch mutated = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AdminProductVariantUpsertRequest variant = new AdminProductVariantUpsertRequest(
                variantId, variants.findById(variantId).orElseThrow().getSku(), UnitType.UNIT, 100, "unit",
                BigDecimal.ONE, 5, 2, true, null);
        AdminProductUpsertRequest stale = new AdminProductUpsertRequest(
                "Bulk target", products.findById(productId).orElseThrow().getSlug(), "Bulk target", true,
                categoryId, ProductType.GRANEL, InventoryPolicy.BULK_WEIGHT, 1000, List.of(variant), List.of());

        try (var executor = Executors.newFixedThreadPool(2)) {
            var receipt = executor.submit(() -> transactions.executeWithoutResult(status -> {
                inventory.apply(List.of(InventoryDelta.bulk(productId, 100)));
                mutated.countDown();
                try { release.await(5, TimeUnit.SECONDS); } catch (InterruptedException exception) { throw new RuntimeException(exception); }
            }));
            mutated.await(5, TimeUnit.SECONDS);
            var admin = executor.submit(() -> {
                try { transactions.executeWithoutResult(status -> adminProducts.update(productId, stale)); return false; }
                catch (ProcurementConflictException expected) { return true; }
            });
            Thread.sleep(200);
            release.countDown();
            receipt.get(10, TimeUnit.SECONDS);
            assertTrue(admin.get(10, TimeUnit.SECONDS));
        }
        assertEquals(1100, products.findById(productId).orElseThrow().getStockBaseGrams());
    }

    private void mutateAfterBarrier(CountDownLatch ready, CountDownLatch start, List<InventoryDelta> deltas) throws InterruptedException {
        ready.countDown();
        start.await(5, TimeUnit.SECONDS);
        transactions.executeWithoutResult(status -> inventory.apply(deltas));
    }
}
