package com.eltano.ecommerce.catalog.jobs.api.dto;

import java.time.Instant;
import java.util.UUID;

public record AdminCatalogJobRowResponse(
        UUID id,
        int rowNumber,
        String outcome,
        String errorCode,
        String errorMessage,
        String payload,
        Instant createdAt) {
}
