package com.eltano.ecommerce.orders.api.dto;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.eltano.ecommerce.orders.domain.FulfillmentMethod;

public record CreateOrderDraftRequest(
        @NotBlank @Size(max = 180) String customerName,
        @NotBlank @Size(max = 60) String phone,
        @Size(max = 1000) String note,
        @NotNull FulfillmentMethod fulfillmentMethod,
        @Size(max = 500) String deliveryAddress,
        @Size(max = 120) String pickupTime,
        @NotEmpty List<@Valid Item> items) {

    public record Item(
            @NotNull UUID variantId,
            @NotNull @Min(1) Integer quantity) {
    }
}
