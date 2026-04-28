package com.eltano.ecommerce.catalog.api.dto;

import java.time.Instant;
import java.util.UUID;

public record AdminProductImageResponse(
        UUID id,
        String url,
        String altText,
        int sortOrder,
        boolean primary,
        Instant createdAt,
        Instant updatedAt) {
}
