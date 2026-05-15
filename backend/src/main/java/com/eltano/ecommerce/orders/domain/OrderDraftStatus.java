package com.eltano.ecommerce.orders.domain;

public enum OrderDraftStatus {
    DRAFT,
    PAYMENT_PENDING,
    PAID,
    PREPARING,
    READY,
    DELIVERED,
    FAILED,
    CANCELLED,
    EXPIRED
}
