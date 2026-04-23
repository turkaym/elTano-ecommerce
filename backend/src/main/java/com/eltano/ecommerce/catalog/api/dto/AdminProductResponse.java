package com.eltano.ecommerce.catalog.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.eltano.ecommerce.catalog.domain.InventoryPolicy;
import com.eltano.ecommerce.catalog.domain.ProductType;

public record AdminProductResponse(
        UUID id,
        String name,
        String slug,
        String description,
        boolean active,
        UUID categoryId,
        String categoryName,
        String categorySlug,
        ProductType productType,
        InventoryPolicy inventoryPolicy,
        Integer stockBaseGrams,
        List<AdminProductVariantResponse> variants,
        Instant createdAt,
        Instant updatedAt) {
}
