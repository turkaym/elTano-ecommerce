package com.eltano.ecommerce.orders.payment.mercadopago;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

public interface MercadoPagoClient {

    Preference createPreference(UUID draftId, String reference, String currency, BigDecimal total);

    Payment getPayment(String paymentExternalId);

    record Preference(String preferenceId, String initPoint) {
    }

    record Payment(
            String paymentExternalId,
            String status,
            String statusDetail,
            String externalReference,
            Map<String, String> metadata) {
    }
}
