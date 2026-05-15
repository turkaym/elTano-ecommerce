package com.eltano.ecommerce.orders.api.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
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
        Payment payment,
        List<Item> items,
        Instant createdAt,
        Instant updatedAt) {

    public record Payment(
            String provider,
            String preferenceId,
            String externalId,
            String statusDetail,
            Instant updatedAt) {
    }

    public record Item(
            UUID id,
            UUID variantId,
            String productName,
            String unitLabel,
            BigDecimal unitPrice,
            int quantity,
            BigDecimal subtotal) {
    }
}
