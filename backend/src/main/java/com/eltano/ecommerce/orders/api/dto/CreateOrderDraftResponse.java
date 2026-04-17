package com.eltano.ecommerce.orders.api.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateOrderDraftResponse(
        UUID draftId,
        String reference,
        String currency,
        BigDecimal subtotal,
        BigDecimal total,
        String whatsappMessage) {
}
