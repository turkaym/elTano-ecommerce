package com.eltano.ecommerce.inventory.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.eltano.ecommerce.catalog.domain.Product;
import com.eltano.ecommerce.catalog.domain.ProductVariant;
import com.eltano.ecommerce.catalog.repository.ProductRepository;
import com.eltano.ecommerce.catalog.repository.ProductVariantRepository;

@ExtendWith(MockitoExtension.class)
class InventoryMutationServiceTest {
    @Mock ProductRepository products;
    @Mock ProductVariantRepository variants;

    @Test
    void appliesSortedMixedTargetDeltasWithoutChangingReservations() {
        UUID productId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        Product product = new Product();
        ReflectionTestUtils.setField(product, "id", productId);
        product.setStockBaseGrams(1000);
        product.setStockReservedBaseGrams(200);
        ProductVariant variant = new ProductVariant();
        ReflectionTestUtils.setField(variant, "id", variantId);
        variant.setStockAvailable(5);
        variant.setStockReserved(2);
        when(products.findAllByIdInForUpdate(List.of(productId))).thenReturn(List.of(product));
        when(variants.findAllByIdInForUpdate(List.of(variantId))).thenReturn(List.of(variant));

        var result = new InventoryMutationService(products, variants).apply(List.of(
                InventoryDelta.bulk(productId, 500), InventoryDelta.variant(variantId, 3)));

        assertEquals(1500, product.getStockBaseGrams());
        assertEquals(200, product.getStockReservedBaseGrams());
        assertEquals(8, variant.getStockAvailable());
        assertEquals(2, variant.getStockReserved());
        assertEquals(List.of(1000, 5), result.stream().map(InventoryMutation::beforeBalance).toList());
    }

    @Test
    void blocksReversalBelowReservationsBeforeChangingAnyBalance() {
        UUID productId = UUID.randomUUID();
        Product product = new Product();
        ReflectionTestUtils.setField(product, "id", productId);
        product.setStockBaseGrams(1000);
        product.setStockReservedBaseGrams(800);
        when(products.findAllByIdInForUpdate(List.of(productId))).thenReturn(List.of(product));

        assertThrows(InventoryInvariantException.class,
                () -> new InventoryMutationService(products, variants).apply(List.of(InventoryDelta.bulk(productId, -300))));
        assertEquals(1000, product.getStockBaseGrams());
    }

    @Test
    void allowsVariantAvailableBalanceBelowReservedWhenItRemainsNonNegative() {
        UUID variantId = UUID.randomUUID();
        ProductVariant variant = variant(variantId, 5, 4);
        when(variants.findAllByIdInForUpdate(List.of(variantId))).thenReturn(List.of(variant));

        var result = new InventoryMutationService(products, variants).apply(
                List.of(InventoryDelta.variant(variantId, -2)));

        assertEquals(3, variant.getStockAvailable());
        assertEquals(4, variant.getStockReserved());
        assertEquals(3, result.getFirst().afterBalance());
    }

    @Test
    void aggregatesDuplicateTargetDeltasIntoOneContinuousMutation() {
        UUID productId = UUID.randomUUID();
        Product product = product(productId, 1000, 100);
        when(products.findAllByIdInForUpdate(List.of(productId))).thenReturn(List.of(product));

        var result = new InventoryMutationService(products, variants).apply(List.of(
                InventoryDelta.bulk(productId, 200),
                InventoryDelta.bulk(productId, 300)));

        assertEquals(1500, product.getStockBaseGrams());
        assertEquals(1, result.size());
        assertEquals(500, result.getFirst().delta());
        assertEquals(1000, result.getFirst().beforeBalance());
        assertEquals(1500, result.getFirst().afterBalance());
    }

    @Test
    void requestsPessimisticLocksInAscendingProductThenVariantOrder() {
        UUID highProduct = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
        UUID lowProduct = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID highVariant = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");
        UUID lowVariant = UUID.fromString("00000000-0000-0000-0000-000000000002");
        when(products.findAllByIdInForUpdate(List.of(lowProduct, highProduct))).thenReturn(List.of(
                product(lowProduct, 10, 0), product(highProduct, 10, 0)));
        when(variants.findAllByIdInForUpdate(List.of(lowVariant, highVariant))).thenReturn(List.of(
                variant(lowVariant, 10, 0), variant(highVariant, 10, 0)));

        new InventoryMutationService(products, variants).apply(List.of(
                InventoryDelta.variant(highVariant, 1), InventoryDelta.bulk(highProduct, 1),
                InventoryDelta.variant(lowVariant, 1), InventoryDelta.bulk(lowProduct, 1)));

        verify(products).findAllByIdInForUpdate(List.of(lowProduct, highProduct));
        verify(variants).findAllByIdInForUpdate(List.of(lowVariant, highVariant));
    }

    private Product product(UUID id, int available, int reserved) {
        Product product = new Product();
        ReflectionTestUtils.setField(product, "id", id);
        product.setStockBaseGrams(available);
        product.setStockReservedBaseGrams(reserved);
        return product;
    }

    private ProductVariant variant(UUID id, int available, int reserved) {
        ProductVariant variant = new ProductVariant();
        ReflectionTestUtils.setField(variant, "id", id);
        variant.setStockAvailable(available);
        variant.setStockReserved(reserved);
        return variant;
    }
}
