package com.eltano.ecommerce.catalog.api.dto;

import java.time.Instant;
import java.util.UUID;

public record AdminCategoryResponse(
        UUID id,
        String name,
        String slug,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {
}
