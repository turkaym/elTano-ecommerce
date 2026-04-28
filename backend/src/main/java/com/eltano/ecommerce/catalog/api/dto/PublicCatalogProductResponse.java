package com.eltano.ecommerce.catalog.api.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.eltano.ecommerce.catalog.domain.InventoryPolicy;
import com.eltano.ecommerce.catalog.domain.ProductType;
import com.eltano.ecommerce.catalog.domain.UnitType;

public record PublicCatalogProductResponse(
        UUID id,
        String name,
        String slug,
        String description,
        String categoryName,
        String categorySlug,
        ProductType productType,
        InventoryPolicy inventoryPolicy,
        Integer stockBaseGrams,
        List<PublicCatalogImageResponse> images,
        List<PublicCatalogVariantResponse> variants) {

    public record PublicCatalogImageResponse(
            UUID id,
            String url,
            String altText,
            int sortOrder,
            boolean primary) {
    }

    public record PublicCatalogVariantResponse(
            UUID id,
            String sku,
            UnitType unitType,
            Integer weightGrams,
            String unitLabel,
            BigDecimal price,
            int stockAvailable,
            int stockReserved,
            String attributesJson) {
    }
}
