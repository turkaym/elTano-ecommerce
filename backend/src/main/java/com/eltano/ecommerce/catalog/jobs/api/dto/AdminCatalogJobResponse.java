package com.eltano.ecommerce.catalog.jobs.api.dto;

import java.time.Instant;
import java.util.UUID;

public record AdminCatalogJobResponse(
        UUID id,
        String jobType,
        String status,
        String createdBy,
        String sourceFormat,
        String summary,
        Instant createdAt,
        Instant updatedAt,
        Instant completedAt) {
}
