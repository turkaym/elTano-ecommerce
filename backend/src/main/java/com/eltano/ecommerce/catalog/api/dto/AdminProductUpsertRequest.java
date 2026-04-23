package com.eltano.ecommerce.catalog.api.dto;

import java.util.List;
import java.util.UUID;

import com.eltano.ecommerce.catalog.domain.InventoryPolicy;
import com.eltano.ecommerce.catalog.domain.ProductType;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record AdminProductUpsertRequest(
        @NotBlank @Size(max = 180) String name,
        @NotBlank @Size(max = 180) String slug,
        @NotBlank @Size(max = 2000) String description,
        @NotNull Boolean active,
        @NotNull UUID categoryId,
        ProductType productType,
        InventoryPolicy inventoryPolicy,
        @PositiveOrZero Integer stockBaseGrams,
        @NotEmpty List<@Valid AdminProductVariantUpsertRequest> variants) {
}
