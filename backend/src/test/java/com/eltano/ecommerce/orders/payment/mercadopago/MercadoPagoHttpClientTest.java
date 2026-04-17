package com.eltano.ecommerce.orders.payment.mercadopago;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class MercadoPagoHttpClientTest {

    private RestClient.Builder restClientBuilder;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        restClientBuilder = RestClient.builder();
        server = MockRestServiceServer.bindTo(restClientBuilder).build();
    }

    @Test
    void createPreferenceOmitsBackUrlsAndAutoReturnForLocalhostUrls() {
        UUID draftId = UUID.fromString("93ab80b2-844e-4a35-9846-52056f8a297c");
        MercadoPagoHttpClient client = new MercadoPagoHttpClient(
                restClientBuilder,
                "https://api.mercadopago.com",
                "test-token",
                "http://localhost:5173/checkout/return",
                "http://localhost:5173/checkout/return?result=failure",
                "http://localhost:5173/checkout/return?result=pending",
                null);

        server.expect(requestTo("https://api.mercadopago.com/checkout/preferences"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.back_urls").doesNotExist())
                .andExpect(jsonPath("$.auto_return").doesNotExist())
                .andExpect(jsonPath("$.notification_url").doesNotExist())
                .andRespond(withSuccess(
                        "{" +
                                "\"id\":\"pref-123\"," +
                                "\"init_point\":\"https://www.mercadopago.com/checkout/v1/redirect?pref_id=pref-123\"" +
                                "}",
                        MediaType.APPLICATION_JSON));

        MercadoPagoClient.Preference preference = client.createPreference(draftId, "ET-2026-A1B2C3", "ARS",
                new BigDecimal("12000.00"));

        assertEquals("pref-123", preference.preferenceId());
        assertEquals("https://www.mercadopago.com/checkout/v1/redirect?pref_id=pref-123", preference.initPoint());
        server.verify();
    }

    @Test
    void createPreferenceSendsAutoReturnApprovedForHttpsPublicSuccessUrl() {
        UUID draftId = UUID.fromString("93ab80b2-844e-4a35-9846-52056f8a297c");
        MercadoPagoHttpClient client = new MercadoPagoHttpClient(
                restClientBuilder,
                "https://api.mercadopago.com",
                "test-token",
                "https://checkout.eltano.com/return",
                "https://checkout.eltano.com/return?result=failure",
                "https://checkout.eltano.com/return?result=pending",
                "https://api.eltano.com/api/payments/mercadopago/webhook");

        server.expect(requestTo("https://api.mercadopago.com/checkout/preferences"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.back_urls.success")
                        .value("https://checkout.eltano.com/return?draftId=" + draftId))
                .andExpect(jsonPath("$.back_urls.failure")
                        .value("https://checkout.eltano.com/return?result=failure&draftId=" + draftId))
                .andExpect(jsonPath("$.back_urls.pending")
                        .value("https://checkout.eltano.com/return?result=pending&draftId=" + draftId))
                .andExpect(jsonPath("$.auto_return").value("approved"))
                .andExpect(jsonPath("$.notification_url")
                        .value("https://api.eltano.com/api/payments/mercadopago/webhook"))
                .andRespond(withSuccess(
                        "{" +
                                "\"id\":\"pref-123\"," +
                                "\"init_point\":\"https://www.mercadopago.com/checkout/v1/redirect?pref_id=pref-123\"" +
                                "}",
                        MediaType.APPLICATION_JSON));

        MercadoPagoClient.Preference preference = client.createPreference(draftId, "ET-2026-A1B2C3", "ARS",
                new BigDecimal("12000.00"));

        assertEquals("pref-123", preference.preferenceId());
        assertEquals("https://www.mercadopago.com/checkout/v1/redirect?pref_id=pref-123", preference.initPoint());
        server.verify();
    }

    @Test
    void createPreferenceFailsFastWhenSuccessUrlIsInvalid() {
        MercadoPagoHttpClient client = new MercadoPagoHttpClient(
                restClientBuilder,
                "https://api.mercadopago.com",
                "test-token",
                "not-a-url",
                "http://localhost:5173/checkout/return?result=failure",
                "http://localhost:5173/checkout/return?result=pending",
                null);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> client.createPreference(UUID.randomUUID(), "ET-2026-A1B2C3", "ARS", new BigDecimal("12000.00")));

        assertTrue(exception.getMessage().contains("MP_CHECKOUT_SUCCESS_URL"));
    }

    @Test
    void createPreferenceFailsFastWhenFailureUrlIsBlank() {
        MercadoPagoHttpClient client = new MercadoPagoHttpClient(
                restClientBuilder,
                "https://api.mercadopago.com",
                "test-token",
                "http://localhost:5173/checkout/return?result=success",
                "   ",
                "http://localhost:5173/checkout/return?result=pending",
                null);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> client.createPreference(UUID.randomUUID(), "ET-2026-A1B2C3", "ARS", new BigDecimal("12000.00")));

        assertTrue(exception.getMessage().contains("MP_CHECKOUT_FAILURE_URL"));
    }

    @Test
    void createPreferenceFailsFastWhenPendingUrlUsesUnsupportedScheme() {
        MercadoPagoHttpClient client = new MercadoPagoHttpClient(
                restClientBuilder,
                "https://api.mercadopago.com",
                "test-token",
                "http://localhost:5173/checkout/return?result=success",
                "http://localhost:5173/checkout/return?result=failure",
                "ftp://localhost/checkout/return?result=pending",
                null);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> client.createPreference(UUID.randomUUID(), "ET-2026-A1B2C3", "ARS", new BigDecimal("12000.00")));

        assertTrue(exception.getMessage().contains("MP_CHECKOUT_PENDING_URL"));
    }

    @Test
    void getPaymentMapsExternalReferenceAndMetadata() {
        MercadoPagoHttpClient client = new MercadoPagoHttpClient(
                restClientBuilder,
                "https://api.mercadopago.com",
                "test-token",
                "https://checkout.eltano.com/return",
                "https://checkout.eltano.com/return?result=failure",
                "https://checkout.eltano.com/return?result=pending",
                null);

        server.expect(requestTo("https://api.mercadopago.com/v1/payments/pay-777"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{" +
                                "\"id\":\"pay-777\"," +
                                "\"status\":\"approved\"," +
                                "\"status_detail\":\"accredited\"," +
                                "\"external_reference\":\"ET-2026-REF01\"," +
                                "\"metadata\":{\"draftId\":\"93ab80b2-844e-4a35-9846-52056f8a297c\"}" +
                                "}",
                        MediaType.APPLICATION_JSON));

        MercadoPagoClient.Payment payment = client.getPayment("pay-777");

        assertEquals("pay-777", payment.paymentExternalId());
        assertEquals("approved", payment.status());
        assertEquals("ET-2026-REF01", payment.externalReference());
        assertEquals("93ab80b2-844e-4a35-9846-52056f8a297c", payment.metadata().get("draftId"));
        server.verify();
    }

    @Test
    void createPreferenceFailsFastWhenNotificationUrlIsInvalid() {
        IllegalStateException exception = assertThrows(IllegalStateException.class,
                () -> new MercadoPagoHttpClient(
                        restClientBuilder,
                        "https://api.mercadopago.com",
                        "test-token",
                        "https://checkout.eltano.com/return?result=success",
                        "https://checkout.eltano.com/return?result=failure",
                        "https://checkout.eltano.com/return?result=pending",
                        "not-a-url"));

        assertTrue(exception.getMessage().contains("MP_NOTIFICATION_URL"));
    }
}
