package com.eltano.ecommerce.catalog.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdminProductResponse(
        UUID id,
        String name,
        String slug,
        String description,
        boolean active,
        UUID categoryId,
        String categoryName,
        String categorySlug,
        List<AdminProductVariantResponse> variants,
        Instant createdAt,
        Instant updatedAt) {
}
