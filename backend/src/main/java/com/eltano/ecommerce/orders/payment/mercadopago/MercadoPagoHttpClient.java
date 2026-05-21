package com.eltano.ecommerce.orders.payment.mercadopago;

import java.math.BigDecimal;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.eltano.ecommerce.common.api.MercadoPagoRequestException;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@Component
public class MercadoPagoHttpClient implements MercadoPagoClient {

    private final RestClient restClient;
    private final String checkoutSuccessUrl;
    private final String checkoutFailureUrl;
    private final String checkoutPendingUrl;
    private final String notificationUrl;

    public MercadoPagoHttpClient(
            RestClient.Builder restClientBuilder,
            @Value("${app.mercadopago.api-base-url:https://api.mercadopago.com}") String apiBaseUrl,
            @Value("${app.mercadopago.access-token:}") String accessToken,
            @Value("${app.mercadopago.checkout-success-url:http://localhost:5173/checkout/return?result=success}") String checkoutSuccessUrl,
            @Value("${app.mercadopago.checkout-failure-url:http://localhost:5173/checkout/return?result=failure}") String checkoutFailureUrl,
            @Value("${app.mercadopago.checkout-pending-url:http://localhost:5173/checkout/return?result=pending}") String checkoutPendingUrl,
            @Value("${app.mercadopago.notification-url:}") String notificationUrl) {
        this.checkoutSuccessUrl = checkoutSuccessUrl;
        this.checkoutFailureUrl = checkoutFailureUrl;
        this.checkoutPendingUrl = checkoutPendingUrl;
        this.notificationUrl = validateOptionalHttpUrl(notificationUrl, "MP_NOTIFICATION_URL");
        this.restClient = restClientBuilder
                .baseUrl(apiBaseUrl)
                .defaultHeader("Authorization", "Bearer " + accessToken)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public Preference createPreference(UUID draftId, String reference, String currency, BigDecimal total) {
        String successUrl = buildReturnUrl(checkoutSuccessUrl, draftId, "MP_CHECKOUT_SUCCESS_URL");
        String failureUrl = buildReturnUrl(checkoutFailureUrl, draftId, "MP_CHECKOUT_FAILURE_URL");
        String pendingUrl = buildReturnUrl(checkoutPendingUrl, draftId, "MP_CHECKOUT_PENDING_URL");

        boolean eligibleSuccessUrl = isMercadoPagoEligibleReturnUrl(successUrl);
        boolean eligibleFailureUrl = isMercadoPagoEligibleReturnUrl(failureUrl);
        boolean eligiblePendingUrl = isMercadoPagoEligibleReturnUrl(pendingUrl);
        boolean includeBackUrls = eligibleSuccessUrl && eligibleFailureUrl && eligiblePendingUrl;

        PreferenceRequest request = new PreferenceRequest(
                reference,
                List.of(new PreferenceItem(draftId.toString(), "Pedido " + reference, 1, currency, total)),
                // Local/non-public return URLs (e.g., localhost/http) are intentionally omitted
                // to avoid Mercado Pago 400 errors while still allowing preference creation.
                includeBackUrls ? new BackUrls(successUrl, failureUrl, pendingUrl) : null,
                includeBackUrls ? "approved" : null,
                notificationUrl,
                Map.of("draftId", draftId.toString()));

        PreferenceResponse response;
        try {
            response = restClient.post()
                    .uri("/checkout/preferences")
                    .body(request)
                    .retrieve()
                    .body(PreferenceResponse.class);
        } catch (RestClientResponseException ex) {
            throw new MercadoPagoRequestException(ex.getStatusCode(), ex.getResponseBodyAsString(), ex);
        }

        if (response == null || response.id() == null || response.initPoint() == null) {
            throw new IllegalStateException("Mercado Pago preference response missing required fields");
        }

        return new Preference(response.id(), response.initPoint());
    }

    @Override
    public Payment getPayment(String paymentExternalId) {
        PaymentResponse response;
        try {
            response = restClient.get()
                    .uri("/v1/payments/{paymentId}", paymentExternalId)
                    .retrieve()
                    .body(PaymentResponse.class);
        } catch (RestClientResponseException ex) {
            throw new MercadoPagoRequestException(ex.getStatusCode(), ex.getResponseBodyAsString(), ex);
        }

        if (response == null || response.id() == null || response.status() == null) {
            throw new IllegalStateException("Mercado Pago payment response missing required fields");
        }

        return new Payment(
                response.id(),
                response.status(),
                response.statusDetail(),
                response.externalReference(),
                response.metadata() == null ? Collections.emptyMap() : response.metadata());
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record PreferenceRequest(
            @JsonProperty("external_reference") String externalReference,
            List<PreferenceItem> items,
            @JsonProperty("back_urls") BackUrls backUrls,
            // MP requires auto_return=approved only when a usable success return URL exists.
            // We intentionally omit this field when success is unavailable to avoid provider 400s.
            @JsonProperty("auto_return") String autoReturn,
            @JsonProperty("notification_url") String notificationUrl,
            Map<String, String> metadata) {
    }

    private String buildReturnUrl(String configuredUrl, UUID draftId, String configKey) {
        String validatedBaseUrl = validateCheckoutUrl(configuredUrl, configKey);
        return UriComponentsBuilder
                .fromUriString(validatedBaseUrl)
                .queryParam("draftId", draftId)
                .build(true)
                .toUriString();
    }

    private String validateCheckoutUrl(String configuredUrl, String configKey) {
        if (!StringUtils.hasText(configuredUrl)) {
            throw new IllegalStateException("Invalid " + configKey + ": value must be a non-blank absolute http/https URL");
        }

        String trimmed = configuredUrl.trim();
        URI uri;
        try {
            uri = URI.create(trimmed);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Invalid " + configKey + ": value must be a non-blank absolute http/https URL", ex);
        }

        String scheme = uri.getScheme();
        boolean httpScheme = "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
        if (!uri.isAbsolute() || !httpScheme || uri.getHost() == null) {
            throw new IllegalStateException("Invalid " + configKey + ": value must be a non-blank absolute http/https URL");
        }

        return trimmed;
    }

    private String validateOptionalHttpUrl(String configuredUrl, String configKey) {
        if (!StringUtils.hasText(configuredUrl)) {
            return null;
        }
        return validateCheckoutUrl(configuredUrl, configKey);
    }

    private boolean isMercadoPagoEligibleReturnUrl(String returnUrl) {
        if (!StringUtils.hasText(returnUrl)) {
            return false;
        }

        URI returnUri;
        try {
            returnUri = URI.create(returnUrl);
        } catch (IllegalArgumentException ex) {
            return false;
        }

        if (!returnUri.isAbsolute() || !"https".equalsIgnoreCase(returnUri.getScheme())) {
            return false;
        }

        String host = returnUri.getHost();
        if (!StringUtils.hasText(host)) {
            return false;
        }

        return !isLoopbackHost(host);
    }

    private boolean isLoopbackHost(String host) {
        String normalizedHost = host.trim().toLowerCase();
        return "localhost".equals(normalizedHost)
                || "127.0.0.1".equals(normalizedHost)
                || "::1".equals(normalizedHost)
                || "0:0:0:0:0:0:0:1".equals(normalizedHost);
    }

    private record PreferenceItem(
            String id,
            String title,
            int quantity,
            @JsonProperty("currency_id") String currencyId,
            @JsonProperty("unit_price") BigDecimal unitPrice) {
    }

    private record BackUrls(String success, String failure, String pending) {
    }

    private record PreferenceResponse(String id, @JsonProperty("init_point") String initPoint) {
    }

    private record PaymentResponse(
            String id,
            String status,
            @JsonProperty("status_detail") String statusDetail,
            @JsonProperty("external_reference") String externalReference,
            Map<String, String> metadata) {
    }
}
