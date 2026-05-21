package com.eltano.ecommerce.catalog.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.eltano.ecommerce.catalog.api.dto.PublicCatalogProductResponse;
import com.eltano.ecommerce.catalog.domain.Category;
import com.eltano.ecommerce.catalog.domain.InventoryPolicy;
import com.eltano.ecommerce.catalog.domain.Product;
import com.eltano.ecommerce.catalog.domain.ProductType;
import com.eltano.ecommerce.catalog.domain.ProductVariant;
import com.eltano.ecommerce.catalog.domain.UnitType;
import com.eltano.ecommerce.catalog.repository.ProductRepository;

@ExtendWith(MockitoExtension.class)
class CatalogQueryServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Test
    void listUsesSharedAvailableGramsForBulkWeightVariantAvailability() {
        Product product = product(ProductType.GRANEL, InventoryPolicy.BULK_WEIGHT, 600, 350);
        product.addVariant(variant("GR-100", 100, "100g", 0, 0));
        product.addVariant(variant("GR-250", 250, "250g", 99, 0));
        product.addVariant(variant("GR-500", 500, "500g", 99, 0));
        when(productRepository.searchPublicCatalog(null)).thenReturn(List.of(product));

        PublicCatalogProductResponse response = new CatalogQueryService(productRepository).list(null, null).getFirst();

        assertEquals(250, response.stockAvailableBaseGrams());
        assertEquals(350, response.stockReservedBaseGrams());
        assertEquals(2, response.variants().get(0).stockAvailable());
        assertEquals(1, response.variants().get(1).stockAvailable());
        assertEquals(0, response.variants().get(2).stockAvailable());
    }

    @Test
    void listPreservesPerVariantAvailabilityForPackagedProducts() {
        Product product = product(ProductType.ENVASADO, InventoryPolicy.PER_VARIANT, null, 0);
        product.addVariant(variant("ENV-500", 500, "500g", 7, 2));
        when(productRepository.searchPublicCatalog(null)).thenReturn(List.of(product));

        PublicCatalogProductResponse response = new CatalogQueryService(productRepository).list(null, null).getFirst();

        assertEquals(null, response.stockAvailableBaseGrams());
        assertEquals(7, response.variants().getFirst().stockAvailable());
        assertEquals(2, response.variants().getFirst().stockReserved());
    }

    private Product product(ProductType type, InventoryPolicy policy, Integer stockBaseGrams, int stockReservedBaseGrams) {
        Category category = new Category();
        category.setName("Frutos secos");
        category.setSlug("frutos-secos");

        Product product = new Product();
        ReflectionTestUtils.setField(product, "id", UUID.randomUUID());
        product.setName("Almendra");
        product.setSlug("almendra");
        product.setDescription("Almendra premium");
        product.setCategory(category);
        product.setProductType(type);
        product.setInventoryPolicy(policy);
        product.setStockBaseGrams(stockBaseGrams);
        product.setStockReservedBaseGrams(stockReservedBaseGrams);
        return product;
    }

    private ProductVariant variant(String sku, int grams, String label, int stockAvailable, int stockReserved) {
        ProductVariant variant = new ProductVariant();
        ReflectionTestUtils.setField(variant, "id", UUID.randomUUID());
        variant.setSku(sku);
        variant.setUnitType(UnitType.WEIGHT);
        variant.setWeightGrams(grams);
        variant.setUnitLabel(label);
        variant.setPrice(BigDecimal.valueOf(1200));
        variant.setStockAvailable(stockAvailable);
        variant.setStockReserved(stockReserved);
        variant.setActive(true);
        return variant;
    }
}
