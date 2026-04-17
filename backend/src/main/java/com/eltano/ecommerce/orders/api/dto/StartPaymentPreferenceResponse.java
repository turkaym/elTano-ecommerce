package com.eltano.ecommerce.orders.api.dto;

import java.util.UUID;

public record StartPaymentPreferenceResponse(
        UUID draftId,
        String preferenceId,
        String initPoint) {
}
