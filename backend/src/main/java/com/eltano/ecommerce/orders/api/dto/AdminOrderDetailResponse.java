package com.eltano.ecommerce.orders.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record AdminOrderDetailResponse(
        UUID id,
        String reference,
        String status,
        String customer,
        String phone,
        String note,
        String currency,
        BigDecimal subtotal,
        BigDecimal total,
        Instant createdAt,
        Instant updatedAt) {
}
