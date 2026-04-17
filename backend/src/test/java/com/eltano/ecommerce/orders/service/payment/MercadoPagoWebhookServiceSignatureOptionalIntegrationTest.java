package com.eltano.ecommerce.orders.service.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Map;

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
        "app.mercadopago.webhook-signature-required=false",
        "spring.datasource.url=jdbc:h2:mem:mpwebhooksigoptional;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop"
})
class MercadoPagoWebhookServiceSignatureOptionalIntegrationTest {

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
    void acceptsWebhookWithoutSignatureHeadersWhenSignatureRequirementDisabled() {
        OrderDraft draft = pendingDraft("ET-2026-ITG-OPTIONAL");
        when(mercadoPagoClient.getPayment("pay-optional")).thenReturn(new MercadoPagoClient.Payment(
                "pay-optional",
                "approved",
                "accredited",
                draft.getReference(),
                Map.of("draftId", draft.getId().toString())));

        MercadoPagoWebhookService.Result result = webhookService.process(new MercadoPagoWebhookService.Command(
                "evt-optional",
                "pay-optional",
                draft.getId(),
                null,
                null,
                "{}"));

        assertEquals("PAID_APPLIED", result.outcome());
        OrderDraft saved = orderDraftRepository.findById(draft.getId()).orElseThrow();
        assertEquals(OrderDraftStatus.PAID, saved.getStatus());
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
}
