package com.eltano.ecommerce.orders.api.dto;

import java.util.List;
import java.util.UUID;

public record AdminOrderListResponse(
        List<Item> items,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public record Item(
            UUID id,
            String reference,
            String status,
            String customer,
            String paymentStatus,
            java.math.BigDecimal total,
            java.time.Instant createdAt) {
    }
}
