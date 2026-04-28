package com.eltano.ecommerce.orders.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.eltano.ecommerce.catalog.domain.InventoryPolicy;
import com.eltano.ecommerce.catalog.domain.Product;
import com.eltano.ecommerce.catalog.domain.ProductType;
import com.eltano.ecommerce.catalog.domain.ProductVariant;
import com.eltano.ecommerce.common.api.ConflictException;

class InventoryPolicyServiceTest {

    private InventoryPolicyService inventoryPolicyService;

    @BeforeEach
    void setUp() {
        inventoryPolicyService = new InventoryPolicyService();
    }

    @Test
    void reserveUsesBulkWeightAndDecrementsBaseGrams() {
        ProductVariant variant = bulkVariant(500, 5000);

        inventoryPolicyService.reserve(variant, 2);

        assertEquals(4000, variant.getProduct().getStockBaseGrams());
    }

    @Test
    void reserveThrowsConflictWhenBulkWeightStockIsInsufficient() {
        ProductVariant variant = bulkVariant(500, 900);

        assertThrows(ConflictException.class, () -> inventoryPolicyService.reserve(variant, 2));
    }

    @Test
    void reserveBulkWeightAllowsExactBoundaryWithoutGoingNegative() {
        ProductVariant variant = bulkVariant(500, 1000);

        inventoryPolicyService.reserve(variant, 2);

        assertEquals(0, variant.getProduct().getStockBaseGrams());
    }

    @Test
    void reserveBulkWeightRejectsWhenRequiredGramsExceedByOne() {
        ProductVariant variant = bulkVariant(500, 999);

        assertThrows(ConflictException.class, () -> inventoryPolicyService.reserve(variant, 2));
    }

    @Test
    void reserveAndReleasePerVariantMovesUnitsBetweenAvailableAndReserved() {
        ProductVariant variant = perVariant(10, 1);

        inventoryPolicyService.reserve(variant, 3);
        assertEquals(7, variant.getStockAvailable());
        assertEquals(4, variant.getStockReserved());

        inventoryPolicyService.release(variant, 2);
        assertEquals(9, variant.getStockAvailable());
        assertEquals(2, variant.getStockReserved());
    }

    @Test
    void reservePerVariantThrowsConflictWhenUnitsAreInsufficient() {
        ProductVariant variant = perVariant(1, 0);

        ConflictException ex = assertThrows(ConflictException.class, () -> inventoryPolicyService.reserve(variant, 2));

        assertEquals("Insufficient stock", ex.getMessage());
    }

    @Test
    void reserveAcceptsValidEnvasadoPerVariantTypePolicyCombination() {
        ProductVariant variant = perVariant(5, 0);

        assertDoesNotThrow(() -> inventoryPolicyService.reserve(variant, 1));
        assertEquals(4, variant.getStockAvailable());
        assertEquals(1, variant.getStockReserved());
    }

    @Test
    void releaseBulkWeightAddsConsumedGramsBackToBaseStock() {
        ProductVariant variant = bulkVariant(1000, 7000);

        inventoryPolicyService.reserve(variant, 2);
        inventoryPolicyService.release(variant, 1);

        assertEquals(6000, variant.getProduct().getStockBaseGrams());
    }

    @Test
    void reserveRejectsVariantWhenBulkWeightConfigurationIsMissing() {
        Product product = new Product();
        product.setName("Almendra");
        product.setProductType(ProductType.GRANEL);
        product.setInventoryPolicy(InventoryPolicy.BULK_WEIGHT);
        product.setStockBaseGrams(5000);

        ProductVariant variant = new ProductVariant();
        ReflectionTestUtils.setField(variant, "id", UUID.randomUUID());
        variant.setProduct(product);
        variant.setPrice(new BigDecimal("1000.00"));
        variant.setStockAvailable(10);
        variant.setStockReserved(0);
        variant.setActive(true);
        variant.setSku("SKU-" + UUID.randomUUID());

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> inventoryPolicyService.reserve(variant, 1));
        assertEquals("Variant incompatible with product policy", ex.getMessage());
    }

    @Test
    void reserveFallsBackToPerVariantWhenLegacyPolicyIsNull() {
        Product product = new Product();
        product.setName("Legacy nuez");
        product.setProductType(null);
        product.setInventoryPolicy(null);

        ProductVariant variant = new ProductVariant();
        ReflectionTestUtils.setField(variant, "id", UUID.randomUUID());
        variant.setProduct(product);
        variant.setWeightGrams(null);
        variant.setPrice(new BigDecimal("5000.00"));
        variant.setStockAvailable(3);
        variant.setStockReserved(1);
        variant.setActive(true);
        variant.setSku("SKU-" + UUID.randomUUID());

        inventoryPolicyService.reserve(variant, 2);

        assertEquals(1, variant.getStockAvailable());
        assertEquals(3, variant.getStockReserved());
    }

    private ProductVariant bulkVariant(int weightGrams, int stockBaseGrams) {
        Product product = new Product();
        product.setName("Almendra");
        product.setProductType(ProductType.GRANEL);
        product.setInventoryPolicy(InventoryPolicy.BULK_WEIGHT);
        product.setStockBaseGrams(stockBaseGrams);

        ProductVariant variant = new ProductVariant();
        ReflectionTestUtils.setField(variant, "id", UUID.randomUUID());
        variant.setProduct(product);
        variant.setWeightGrams(weightGrams);
        variant.setPrice(new BigDecimal("1000.00"));
        variant.setStockAvailable(10);
        variant.setStockReserved(0);
        variant.setActive(true);
        variant.setSku("SKU-" + UUID.randomUUID());
        return variant;
    }

    private ProductVariant perVariant(int stockAvailable, int stockReserved) {
        Product product = new Product();
        product.setName("Nuez");
        product.setProductType(ProductType.ENVASADO);
        product.setInventoryPolicy(InventoryPolicy.PER_VARIANT);

        ProductVariant variant = new ProductVariant();
        ReflectionTestUtils.setField(variant, "id", UUID.randomUUID());
        variant.setProduct(product);
        variant.setWeightGrams(500);
        variant.setPrice(new BigDecimal("5000.00"));
        variant.setStockAvailable(stockAvailable);
        variant.setStockReserved(stockReserved);
        variant.setActive(true);
        variant.setSku("SKU-" + UUID.randomUUID());
        return variant;
    }
}
