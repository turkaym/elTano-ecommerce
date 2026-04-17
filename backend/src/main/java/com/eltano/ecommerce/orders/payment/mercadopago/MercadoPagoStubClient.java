package com.eltano.ecommerce.orders.payment.mercadopago;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
@Profile("test")
public class MercadoPagoStubClient implements MercadoPagoClient {

    @Override
    public Preference createPreference(UUID draftId, String reference, String currency, BigDecimal total) {
        String preferenceId = "pref-" + draftId.toString().substring(0, 8);
        String initPoint = "https://www.mercadopago.com.ar/checkout/v1/redirect?pref_id=" + preferenceId;
        return new Preference(preferenceId, initPoint);
    }

    @Override
    public Payment getPayment(String paymentExternalId) {
        return new Payment(paymentExternalId, "approved", "accredited", null, Map.of());
    }
}
