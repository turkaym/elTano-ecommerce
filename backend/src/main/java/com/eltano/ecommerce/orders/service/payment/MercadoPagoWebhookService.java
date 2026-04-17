package com.eltano.ecommerce.orders.service.payment;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.eltano.ecommerce.orders.domain.OrderDraftStatus;
import com.eltano.ecommerce.orders.domain.PaymentWebhookEvent;
import com.eltano.ecommerce.orders.payment.mercadopago.MercadoPagoClient;
import com.eltano.ecommerce.orders.repository.OrderDraftRepository;
import com.eltano.ecommerce.orders.repository.PaymentWebhookEventRepository;
import com.eltano.ecommerce.orders.service.OrderDraftService;

@Service
public class MercadoPagoWebhookService {

    private final PaymentWebhookEventRepository paymentWebhookEventRepository;
    private final OrderDraftRepository orderDraftRepository;
    private final OrderDraftService orderDraftService;
    private final MercadoPagoClient mercadoPagoClient;
    private final String webhookSecret;
    private final boolean webhookSignatureRequired;
    private final long signatureToleranceSeconds;

    public MercadoPagoWebhookService(
            PaymentWebhookEventRepository paymentWebhookEventRepository,
            OrderDraftRepository orderDraftRepository,
            OrderDraftService orderDraftService,
            MercadoPagoClient mercadoPagoClient,
            @Value("${app.mercadopago.webhook-secret:change-me-webhook-secret}") String webhookSecret,
            @Value("${app.mercadopago.webhook-signature-required:true}") boolean webhookSignatureRequired,
            @Value("${app.mercadopago.webhook-signature-tolerance-seconds:300}") long signatureToleranceSeconds) {
        this.paymentWebhookEventRepository = paymentWebhookEventRepository;
        this.orderDraftRepository = orderDraftRepository;
        this.orderDraftService = orderDraftService;
        this.mercadoPagoClient = mercadoPagoClient;
        this.webhookSecret = webhookSecret;
        this.webhookSignatureRequired = webhookSignatureRequired;
        this.signatureToleranceSeconds = signatureToleranceSeconds;
    }

    @Transactional
    public Result process(Command command) {
        String effectiveProviderEventId = resolveProviderEventId(command);

        if (!isValidSignature(command)) {
            throw new InvalidWebhookSignatureException();
        }

        if (paymentWebhookEventRepository.existsByProviderEventId(effectiveProviderEventId)) {
            return new Result(true, "DUPLICATE_IGNORED");
        }

        if (command.paymentExternalId() == null || command.paymentExternalId().isBlank()) {
            persistEvent(effectiveProviderEventId, command, "PAYMENT_ID_MISSING");
            return new Result(true, "PAYMENT_ID_MISSING");
        }

        MercadoPagoClient.Payment payment = mercadoPagoClient.getPayment(command.paymentExternalId());
        if (!isVerifiedPayment(command.paymentExternalId(), payment)) {
            persistEvent(effectiveProviderEventId, command, "PAYMENT_NOT_VERIFIED");
            return new Result(true, "PAYMENT_NOT_VERIFIED");
        }

        UUID draftId = resolveDraftId(command, payment);
        if (draftId == null || orderDraftRepository.findById(draftId).isEmpty()) {
            persistEvent(effectiveProviderEventId, command, "DRAFT_NOT_FOUND");
            return new Result(true, "DRAFT_NOT_FOUND");
        }

        OrderDraftStatus mappedStatus = mapProviderStatus(payment.status());
        OrderDraftService.PaymentTransitionResult transitionResult = orderDraftService.applyPaymentTransition(
                new OrderDraftService.PaymentTransitionCommand(
                        draftId,
                        payment.paymentExternalId(),
                        mappedStatus,
                        payment.statusDetail()));

        persistEvent(effectiveProviderEventId, command, transitionResult.outcome());
        return new Result(true, transitionResult.outcome());
    }

    private void persistEvent(String providerEventId, Command command, String outcome) {
        PaymentWebhookEvent event = new PaymentWebhookEvent();
        event.setProvider("mercadopago");
        event.setProviderEventId(providerEventId);
        event.setPaymentExternalId(command.paymentExternalId());
        event.setPayloadHash(hashPayload(command.rawPayload()));
        event.setOutcome(outcome);
        event.setProcessedAt(Instant.now());
        try {
            paymentWebhookEventRepository.save(event);
        } catch (DataIntegrityViolationException ignored) {
            // concurrent duplicate delivery already persisted the event
        }
    }

    private String resolveProviderEventId(Command command) {
        if (hasText(command.providerEventId())) {
            return command.providerEventId().trim();
        }
        if (hasText(command.paymentExternalId())) {
            return "payment:" + command.paymentExternalId().trim();
        }
        if (hasText(command.rawPayload())) {
            String hashPrefix = hashPayload(command.rawPayload()).substring(0, 32);
            return "payload:" + hashPrefix;
        }
        return "request:" + (hasText(command.requestId()) ? command.requestId().trim() : "unknown");
    }

    private UUID resolveDraftId(Command command, MercadoPagoClient.Payment payment) {
        UUID fromMetadata = parseUuid(metadataValue(payment, "draftId"));
        if (fromMetadata != null) {
            return fromMetadata;
        }

        UUID fromSnakeMetadata = parseUuid(metadataValue(payment, "draft_id"));
        if (fromSnakeMetadata != null) {
            return fromSnakeMetadata;
        }

        UUID fromExternalReference = resolveByExternalReference(payment.externalReference());
        if (fromExternalReference != null) {
            return fromExternalReference;
        }

        return command.draftId();
    }

    private String metadataValue(MercadoPagoClient.Payment payment, String key) {
        if (payment == null || payment.metadata() == null) {
            return null;
        }
        return payment.metadata().get(key);
    }

    private UUID resolveByExternalReference(String externalReference) {
        if (!hasText(externalReference)) {
            return null;
        }

        UUID asUuid = parseUuid(externalReference);
        if (asUuid != null) {
            return asUuid;
        }

        Optional<UUID> fromReference = orderDraftRepository.findByReference(externalReference.trim())
                .map(draft -> draft.getId());
        return fromReference.orElse(null);
    }

    private UUID parseUuid(String value) {
        if (!hasText(value)) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String hashPayload(String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest((payload == null ? "" : payload).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Cannot hash webhook payload", ex);
        }
    }

    private boolean isValidSignature(Command command) {
        if (!webhookSignatureRequired) {
            return true;
        }

        if (!hasText(webhookSecret) || "change-me-webhook-secret".equals(webhookSecret.trim())) {
            return true;
        }

        if (command.signature() == null || command.signature().isBlank()
                || command.requestId() == null || command.requestId().isBlank()) {
            return false;
        }

        if (signatureIdCandidates(command).isEmpty()) {
            return false;
        }

        SignatureParts signatureParts = parseSignature(command.signature());
        if (signatureParts == null) {
            return false;
        }

        long delta = Math.abs(Instant.now().getEpochSecond() - signatureParts.tsEpochSeconds());
        if (delta > signatureToleranceSeconds) {
            return false;
        }

        for (String idCandidate : signatureIdCandidates(command)) {
            String manifest = "id:" + idCandidate + ";request-id:" + command.requestId()
                    + ";ts:" + signatureParts.tsEpochSeconds() + ";";
            String expectedDigest = hmacSha256Hex(manifest, webhookSecret);
            if (MessageDigest.isEqual(
                    expectedDigest.getBytes(StandardCharsets.UTF_8),
                    signatureParts.v1().getBytes(StandardCharsets.UTF_8))) {
                return true;
            }
        }

        return false;
    }

    private Set<String> signatureIdCandidates(Command command) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        if (hasText(command.paymentExternalId())) {
            candidates.add(command.paymentExternalId().trim());
        }
        if (hasText(command.providerEventId())) {
            candidates.add(command.providerEventId().trim());
        }
        if (hasText(command.requestId())) {
            candidates.add("request:" + command.requestId().trim());
        }
        return candidates;
    }

    private SignatureParts parseSignature(String signature) {
        Map<String, String> values = new HashMap<>();
        for (String part : signature.split(",")) {
            String[] keyValue = part.trim().split("=", 2);
            if (keyValue.length != 2) {
                continue;
            }
            values.put(keyValue[0].trim().toLowerCase(Locale.ROOT), keyValue[1].trim());
        }

        String ts = values.get("ts");
        String v1 = values.get("v1");
        if (ts == null || v1 == null || v1.isBlank()) {
            return null;
        }

        try {
            return new SignatureParts(Long.parseLong(ts), v1.toLowerCase(Locale.ROOT));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String hmacSha256Hex(String payload, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(key);
            return HexFormat.of().formatHex(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot verify webhook signature", ex);
        }
    }

    private boolean isVerifiedPayment(String requestedPaymentId, MercadoPagoClient.Payment payment) {
        if (payment == null || payment.paymentExternalId() == null || payment.status() == null) {
            return false;
        }
        return requestedPaymentId.equals(payment.paymentExternalId());
    }

    private OrderDraftStatus mapProviderStatus(String status) {
        String normalized = status == null ? "" : status.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "approved" -> OrderDraftStatus.PAID;
            case "rejected" -> OrderDraftStatus.FAILED;
            case "cancelled", "canceled" -> OrderDraftStatus.CANCELLED;
            case "expired" -> OrderDraftStatus.EXPIRED;
            default -> OrderDraftStatus.PAYMENT_PENDING;
        };
    }

    public record Command(
            String providerEventId,
            String paymentExternalId,
            java.util.UUID draftId,
            String signature,
            String requestId,
            String rawPayload) {
    }

    public record Result(boolean received, String outcome) {
    }

    private record SignatureParts(long tsEpochSeconds, String v1) {
    }

    public static class InvalidWebhookSignatureException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
