package com.eltano.ecommerce.catalog.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdminCategoryUpsertRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Size(max = 120) String slug,
        @NotNull Boolean active) {
}
