package com.eltano.ecommerce.catalog.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.eltano.ecommerce.catalog.domain.UnitType;

public record AdminProductVariantResponse(
        UUID id,
        String sku,
        UnitType unitType,
        Integer weightGrams,
        String unitLabel,
        BigDecimal price,
        int stockAvailable,
        int stockReserved,
        boolean active,
        String attributesJson,
        Instant createdAt,
        Instant updatedAt) {
}
