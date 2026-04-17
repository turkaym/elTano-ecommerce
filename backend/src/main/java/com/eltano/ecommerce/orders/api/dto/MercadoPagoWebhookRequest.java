package com.eltano.ecommerce.orders.api.dto;

import java.util.UUID;

public record MercadoPagoWebhookRequest(
        String id,
        String topic,
        UUID draftId,
        String paymentId) {
}
