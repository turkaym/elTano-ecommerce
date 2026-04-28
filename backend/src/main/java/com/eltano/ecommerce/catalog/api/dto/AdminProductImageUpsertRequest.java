package com.eltano.ecommerce.catalog.api.dto;

import java.util.UUID;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdminProductImageUpsertRequest(
        UUID id,
        @NotBlank String url,
        @Size(max = 180) String altText,
        @NotNull @Min(0) Integer sortOrder,
        @NotNull Boolean primary) {
}
