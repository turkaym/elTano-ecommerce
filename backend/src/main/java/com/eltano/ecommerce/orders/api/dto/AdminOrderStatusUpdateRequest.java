package com.eltano.ecommerce.orders.api.dto;

import jakarta.validation.constraints.NotBlank;

public record AdminOrderStatusUpdateRequest(
        @NotBlank(message = "Status is required") String status) {
}
