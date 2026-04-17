package com.eltano.ecommerce.orders.api;

import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.eltano.ecommerce.orders.service.payment.MercadoPagoWebhookService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Validated
@RestController
@RequestMapping("/api/payments/mercadopago")
public class MercadoPagoWebhookController {

    private final MercadoPagoWebhookService webhookService;
    private final ObjectMapper objectMapper;

    public MercadoPagoWebhookController(MercadoPagoWebhookService webhookService, ObjectMapper objectMapper) {
        this.webhookService = webhookService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/webhook")
    public ResponseEntity<WebhookResponse> receiveWebhook(
            @RequestBody(required = false) String rawBody,
            @RequestParam Map<String, String> queryParams,
            @RequestHeader(value = "x-signature", required = false) String signature,
            @RequestHeader(value = "x-request-id", required = false) String requestId) {

        JsonNode body = parseBody(rawBody);
        String normalizedType = firstNonBlank(
                readText(body, "type"),
                readText(body, "topic"),
                queryParams.get("type"),
                queryParams.get("topic"));

        String paymentId = firstNonBlank(
                readText(body, "paymentId"),
                readText(body, "data", "id"),
                queryParams.get("data.id"));

        if (isPaymentNotification(normalizedType, firstNonBlank(readText(body, "action"), queryParams.get("action")))) {
            paymentId = firstNonBlank(paymentId, queryParams.get("id"));
        }

        String providerEventId = firstNonBlank(
                readText(body, "id"),
                queryParams.get("id"),
                paymentId);

        MercadoPagoWebhookService.Result result = webhookService.process(new MercadoPagoWebhookService.Command(
                providerEventId,
                paymentId,
                parseUuid(firstNonBlank(readText(body, "draftId"), queryParams.get("draftId"))),
                signature,
                requestId,
                rawBody));

        return ResponseEntity.ok(new WebhookResponse(result.received(), result.outcome()));
    }

    private JsonNode parseBody(String rawBody) {
        if (rawBody == null || rawBody.isBlank()) {
            return objectMapper.nullNode();
        }

        try {
            return objectMapper.readTree(rawBody);
        } catch (Exception ex) {
            return objectMapper.nullNode();
        }
    }

    private String readText(JsonNode body, String field) {
        if (body == null || body.isNull()) {
            return null;
        }

        JsonNode node = body.get(field);
        if (node == null || node.isNull()) {
            return null;
        }
        return node.asText(null);
    }

    private String readText(JsonNode body, String field, String nestedField) {
        if (body == null || body.isNull()) {
            return null;
        }

        JsonNode firstLevel = body.get(field);
        if (firstLevel == null || firstLevel.isNull()) {
            return null;
        }

        JsonNode nested = firstLevel.get(nestedField);
        if (nested == null || nested.isNull()) {
            return null;
        }
        return nested.asText(null);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private UUID parseUuid(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return null;
        }

        try {
            return UUID.fromString(candidate.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private boolean isPaymentNotification(String typeOrTopic, String action) {
        String normalizedType = typeOrTopic == null ? "" : typeOrTopic.trim().toLowerCase();
        if ("payment".equals(normalizedType)) {
            return true;
        }

        String normalizedAction = action == null ? "" : action.trim().toLowerCase();
        return normalizedAction.startsWith("payment.");
    }

    public record WebhookResponse(boolean received, String outcome) {
    }
}
