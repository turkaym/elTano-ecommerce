package com.eltano.ecommerce.catalog.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

import com.eltano.ecommerce.catalog.domain.UnitType;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdminProductVariantUpsertRequest(
        UUID id,
        @NotBlank @Size(max = 120) String sku,
        @NotNull UnitType unitType,
        Integer weightGrams,
        @Size(max = 40) String unitLabel,
        @NotNull @DecimalMin(value = "0.01") BigDecimal price,
        @NotNull @Min(0) Integer stockAvailable,
        @NotNull @Min(0) Integer stockReserved,
        @NotNull Boolean active,
        @Size(max = 4000) String attributesJson) {

    @AssertTrue(message = "weightGrams is required for WEIGHT and unitLabel for UNIT")
    public boolean isMeasurementValid() {
        if (unitType == null) {
            return true;
        }
        if (unitType == UnitType.WEIGHT) {
            return weightGrams != null && weightGrams > 0;
        }
        return unitLabel != null && !unitLabel.isBlank();
    }

    @AssertTrue(message = "stockReserved must be less than or equal to stockAvailable")
    public boolean isStockReservationValid() {
        if (stockAvailable == null || stockReserved == null) {
            return true;
        }
        return stockReserved <= stockAvailable;
    }
}
