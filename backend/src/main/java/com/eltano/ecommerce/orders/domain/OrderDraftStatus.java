package com.eltano.ecommerce.orders.domain;

public enum OrderDraftStatus {
    DRAFT,
    PAYMENT_PENDING,
    PAID,
    FAILED,
    CANCELLED,
    EXPIRED
}
