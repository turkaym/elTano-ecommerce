package com.eltano.ecommerce.orders.repository;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import com.eltano.ecommerce.orders.domain.OrderDraft;
import com.eltano.ecommerce.orders.domain.OrderDraftStatus;
import com.eltano.ecommerce.orders.domain.PaymentWebhookEvent;

@DataJpaTest
class PaymentWebhookEventRepositoryTest {

    @Autowired
    private PaymentWebhookEventRepository paymentWebhookEventRepository;

    @Autowired
    private OrderDraftRepository orderDraftRepository;

    @Test
    void providerEventIdMustBeUnique() {
        PaymentWebhookEvent first = new PaymentWebhookEvent();
        first.setProviderEventId("evt-123");
        first.setPaymentExternalId("pay-001");
        first.setProvider("mercadopago");
        first.setOutcome("PAID_APPLIED");

        paymentWebhookEventRepository.saveAndFlush(first);

        PaymentWebhookEvent duplicate = new PaymentWebhookEvent();
        duplicate.setProviderEventId("evt-123");
        duplicate.setPaymentExternalId("pay-001");
        duplicate.setProvider("mercadopago");
        duplicate.setOutcome("DUPLICATE_IGNORED");

        DataIntegrityViolationException duplicateViolation = assertThrows(DataIntegrityViolationException.class,
                () -> paymentWebhookEventRepository.saveAndFlush(duplicate));

        assertTrue(duplicateViolation.getMessage().toLowerCase().contains("unique"));
    }

    @Test
    void orderDraftCanBeReadWithPessimisticLockMethod() {
        OrderDraft draft = new OrderDraft();
        draft.setReference("ET-2026-LOCK01");
        draft.setStatus(OrderDraftStatus.DRAFT);
        draft.setCustomerName("Juan Perez");
        draft.setPhone("+5491112345678");
        draft.setCurrency("ARS");
        draft.setSubtotal(new java.math.BigDecimal("1000.00"));
        draft.setTotal(new java.math.BigDecimal("1000.00"));
        orderDraftRepository.saveAndFlush(draft);

        assertTrue(orderDraftRepository.findByIdForUpdate(draft.getId()).isPresent());
    }

    @Test
    void canQueryDuplicateEventByProviderEventId() {
        PaymentWebhookEvent event = new PaymentWebhookEvent();
        event.setProviderEventId("evt-dup-001");
        event.setPaymentExternalId("pay-001");
        event.setProvider("mercadopago");
        event.setOutcome("PAID_APPLIED");
        paymentWebhookEventRepository.saveAndFlush(event);

        assertTrue(paymentWebhookEventRepository.existsByProviderEventId("evt-dup-001"));
    }
}
