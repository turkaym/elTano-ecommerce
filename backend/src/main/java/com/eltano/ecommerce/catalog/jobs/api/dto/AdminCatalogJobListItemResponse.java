package com.eltano.ecommerce.catalog.jobs.api.dto;

import java.time.Instant;
import java.util.UUID;

public record AdminCatalogJobListItemResponse(
        UUID id,
        String type,
        String status,
        Instant createdAt,
        Instant updatedAt) {
}
