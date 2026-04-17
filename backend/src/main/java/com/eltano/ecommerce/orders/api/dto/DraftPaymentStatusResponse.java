package com.eltano.ecommerce.orders.api.dto;

import java.util.UUID;

public record DraftPaymentStatusResponse(
        UUID draftId,
        String reference,
        String status,
        String updatedAt,
        boolean canRetry) {
}
