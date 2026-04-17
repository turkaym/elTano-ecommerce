package com.eltano.ecommerce.orders.service.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import com.eltano.ecommerce.orders.domain.OrderDraft;
import com.eltano.ecommerce.orders.domain.OrderDraftStatus;
import com.eltano.ecommerce.orders.payment.mercadopago.MercadoPagoClient;
import com.eltano.ecommerce.orders.repository.OrderDraftRepository;
import com.eltano.ecommerce.orders.repository.PaymentWebhookEventRepository;

@SpringBootTest(properties = {
        "app.mercadopago.webhook-secret=test-webhook-secret",
        "app.mercadopago.webhook-signature-tolerance-seconds=300",
        "spring.datasource.url=jdbc:h2:mem:mpwebhook;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class MercadoPagoWebhookServiceIntegrationTest {

    @Autowired
    private MercadoPagoWebhookService webhookService;

    @Autowired
    private OrderDraftRepository orderDraftRepository;

    @Autowired
    private PaymentWebhookEventRepository paymentWebhookEventRepository;

    @MockBean
    private MercadoPagoClient mercadoPagoClient;

    @BeforeEach
    void setUp() {
        paymentWebhookEventRepository.deleteAll();
        orderDraftRepository.deleteAll();
    }

    @Test
    void duplicateWebhookDeliveryIsProcessedOnceAndSecondDeliveryIsNoOp() {
        OrderDraft draft = pendingDraft("ET-2026-ITG01");
        when(mercadoPagoClient.getPayment("pay-001")).thenReturn(new MercadoPagoClient.Payment(
                "pay-001",
                "approved",
                "accredited",
                draft.getReference(),
                Map.of("draftId", draft.getId().toString())));

        String requestId = "req-duplicate";
        String signature = signedHeader("pay-001", requestId, Instant.now().getEpochSecond());

        MercadoPagoWebhookService.Result first = webhookService.process(new MercadoPagoWebhookService.Command(
                "evt-duplicate",
                "pay-001",
                draft.getId(),
                signature,
                requestId,
                "{}"));
        MercadoPagoWebhookService.Result second = webhookService.process(new MercadoPagoWebhookService.Command(
                "evt-duplicate",
                "pay-001",
                draft.getId(),
                signature,
                requestId,
                "{}"));

        assertEquals("PAID_APPLIED", first.outcome());
        assertEquals("DUPLICATE_IGNORED", second.outcome());
        assertEquals(1L, paymentWebhookEventRepository.count());
        OrderDraft saved = orderDraftRepository.findById(draft.getId()).orElseThrow();
        assertEquals(OrderDraftStatus.PAID, saved.getStatus());
    }

    @Test
    void webhookRegressionAfterPaidIsIgnoredAndOrderRemainsPaid() {
        OrderDraft draft = pendingDraft("ET-2026-ITG02");
        String requestId = "req-order";

        when(mercadoPagoClient.getPayment("pay-100")).thenReturn(new MercadoPagoClient.Payment(
                "pay-100",
                "approved",
                "accredited",
                draft.getReference(),
                Map.of("draftId", draft.getId().toString())));
        when(mercadoPagoClient.getPayment("pay-101")).thenReturn(new MercadoPagoClient.Payment(
                "pay-101",
                "rejected",
                "cc_rejected",
                draft.getReference(),
                Map.of("draftId", draft.getId().toString())));

        MercadoPagoWebhookService.Result paid = webhookService.process(new MercadoPagoWebhookService.Command(
                "evt-paid",
                "pay-100",
                draft.getId(),
                signedHeader("pay-100", requestId, Instant.now().getEpochSecond()),
                requestId,
                "{}"));

        MercadoPagoWebhookService.Result regressive = webhookService.process(new MercadoPagoWebhookService.Command(
                "evt-regressive",
                "pay-101",
                draft.getId(),
                signedHeader("pay-101", requestId, Instant.now().getEpochSecond()),
                requestId,
                "{}"));

        assertEquals("PAID_APPLIED", paid.outcome());
        assertEquals("REGRESSION_IGNORED", regressive.outcome());
        OrderDraft saved = orderDraftRepository.findById(draft.getId()).orElseThrow();
        assertEquals(OrderDraftStatus.PAID, saved.getStatus());
    }

    @Test
    void staleWebhookSignatureIsRejected() {
        OrderDraft draft = pendingDraft("ET-2026-ITG03");
        String requestId = "req-stale";
        long staleTs = Instant.now().minusSeconds(3600).getEpochSecond();

        assertThrows(MercadoPagoWebhookService.InvalidWebhookSignatureException.class, () -> webhookService.process(
                new MercadoPagoWebhookService.Command(
                        "evt-stale",
                        "pay-777",
                        draft.getId(),
                        signedHeader("pay-777", requestId, staleTs),
                        requestId,
                        "{}")));
    }

    @Test
    void missingSignatureHeadersAreRejectedByDefaultStrictMode() {
        OrderDraft draft = pendingDraft("ET-2026-ITG07");

        assertThrows(MercadoPagoWebhookService.InvalidWebhookSignatureException.class, () -> webhookService.process(
                new MercadoPagoWebhookService.Command(
                        "evt-missing-signature",
                        "pay-missing-signature",
                        draft.getId(),
                        null,
                        null,
                        "{}")));
    }

    @Test
    void terminalProviderStatusesMapToExpectedDraftStates() {
        Object[][] matrix = {
                { "rejected", OrderDraftStatus.FAILED },
                { "cancelled", OrderDraftStatus.CANCELLED },
                { "expired", OrderDraftStatus.EXPIRED }
        };

        for (Object[] row : matrix) {
            String providerStatus = (String) row[0];
            OrderDraftStatus expected = (OrderDraftStatus) row[1];
            OrderDraft draft = pendingDraft("ET-2026-MAP-" + providerStatus);
            String requestId = "req-" + providerStatus;
            String eventId = "evt-" + providerStatus;
            String paymentId = "pay-" + providerStatus;

            when(mercadoPagoClient.getPayment(paymentId)).thenReturn(new MercadoPagoClient.Payment(
                    paymentId,
                    providerStatus,
                    providerStatus,
                    draft.getReference(),
                    Map.of("draftId", draft.getId().toString())));

            MercadoPagoWebhookService.Result result = webhookService.process(new MercadoPagoWebhookService.Command(
                    eventId,
                    paymentId,
                    null,
                    signedHeader(paymentId, requestId, Instant.now().getEpochSecond()),
                    requestId,
                    "{}"));

            assertEquals(expected.name() + "_APPLIED", result.outcome());
            OrderDraft saved = orderDraftRepository.findById(draft.getId()).orElseThrow();
            assertEquals(expected, saved.getStatus());
        }
    }

    @Test
    void resolvesDraftFromPaymentMetadataWhenWebhookDraftIsMissing() {
        OrderDraft draft = pendingDraft("ET-2026-ITG04");
        String requestId = "req-meta";

        when(mercadoPagoClient.getPayment("pay-meta")).thenReturn(new MercadoPagoClient.Payment(
                "pay-meta",
                "approved",
                "accredited",
                draft.getReference(),
                Map.of("draftId", draft.getId().toString())));

        MercadoPagoWebhookService.Result result = webhookService.process(new MercadoPagoWebhookService.Command(
                "evt-meta",
                "pay-meta",
                null,
                signedHeader("pay-meta", requestId, Instant.now().getEpochSecond()),
                requestId,
                "{}"));

        assertEquals("PAID_APPLIED", result.outcome());
        OrderDraft saved = orderDraftRepository.findById(draft.getId()).orElseThrow();
        assertEquals(OrderDraftStatus.PAID, saved.getStatus());
    }

    @Test
    void resolvesDraftFromExternalReferenceWhenMetadataMissing() {
        OrderDraft draft = pendingDraft("ET-2026-ITG05");
        String requestId = "req-reference";

        when(mercadoPagoClient.getPayment("pay-ref")).thenReturn(new MercadoPagoClient.Payment(
                "pay-ref",
                "approved",
                "accredited",
                draft.getReference(),
                Map.of()));

        MercadoPagoWebhookService.Result result = webhookService.process(new MercadoPagoWebhookService.Command(
                "evt-reference",
                "pay-ref",
                null,
                signedHeader("pay-ref", requestId, Instant.now().getEpochSecond()),
                requestId,
                "{}"));

        assertEquals("PAID_APPLIED", result.outcome());
        OrderDraft saved = orderDraftRepository.findById(draft.getId()).orElseThrow();
        assertEquals(OrderDraftStatus.PAID, saved.getStatus());
    }

    @Test
    void acceptsFallbackProviderEventIdSignatureForCompatibility() {
        OrderDraft draft = pendingDraft("ET-2026-ITG06");
        String requestId = "req-compat";

        when(mercadoPagoClient.getPayment("pay-compat")).thenReturn(new MercadoPagoClient.Payment(
                "pay-compat",
                "approved",
                "accredited",
                draft.getReference(),
                Map.of("draftId", draft.getId().toString())));

        MercadoPagoWebhookService.Result result = webhookService.process(new MercadoPagoWebhookService.Command(
                "evt-compat",
                "pay-compat",
                draft.getId(),
                signedHeader("evt-compat", requestId, Instant.now().getEpochSecond()),
                requestId,
                "{}"));

        assertEquals("PAID_APPLIED", result.outcome());
    }

    private OrderDraft pendingDraft(String reference) {
        OrderDraft draft = new OrderDraft();
        draft.setReference(reference);
        draft.setStatus(OrderDraftStatus.PAYMENT_PENDING);
        draft.setCustomerName("Ana Perez");
        draft.setPhone("+5491198765432");
        draft.setCurrency("ARS");
        draft.setSubtotal(new BigDecimal("15000.00"));
        draft.setTotal(new BigDecimal("15000.00"));
        return orderDraftRepository.saveAndFlush(draft);
    }

    private String signedHeader(String eventId, String requestId, long tsEpochSecond) {
        String manifest = "id:" + eventId + ";request-id:" + requestId + ";ts:" + tsEpochSecond + ";";
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec("test-webhook-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String digest = HexFormat.of().formatHex(mac.doFinal(manifest.getBytes(StandardCharsets.UTF_8)));
            return "ts=" + tsEpochSecond + ",v1=" + digest;
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot build signature", ex);
        }
    }
}
