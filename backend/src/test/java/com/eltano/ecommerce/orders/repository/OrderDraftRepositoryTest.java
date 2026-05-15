package com.eltano.ecommerce.orders.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import com.eltano.ecommerce.orders.domain.OrderDraft;
import com.eltano.ecommerce.orders.domain.OrderDraftStatus;

@DataJpaTest
class OrderDraftRepositoryTest {

    @Autowired
    private OrderDraftRepository orderDraftRepository;

    @Test
    void searchAdminQueryMatchesCustomerPartialCaseInsensitive() {
        OrderDraft farid = saveDraft("ET-2026-A2F8F0", "Farid Gomez", "2026-05-11T14:30:00Z");
        saveDraft("ET-2026-BBBBBB", "Ana Gomez", "2026-05-11T15:00:00Z");

        Page<OrderDraft> page = orderDraftRepository.searchAdmin(
                null,
                Instant.parse("2026-05-11T00:00:00Z"),
                Instant.parse("2026-05-12T00:00:00Z"),
                null,
                null,
                "farid",
                PageRequest.of(0, 20));

        assertThat(page.getContent())
                .extracting(OrderDraft::getId)
                .containsExactly(farid.getId());
    }

    @Test
    void searchAdminQueryMatchesReferencePartialCaseInsensitive() {
        OrderDraft farid = saveDraft("ET-2026-A2F8F0", "Farid Gomez", "2026-05-11T14:30:00Z");
        saveDraft("ET-2026-BBBBBB", "Ana Gomez", "2026-05-11T15:00:00Z");

        Page<OrderDraft> page = orderDraftRepository.searchAdmin(
                null,
                Instant.parse("2026-05-11T00:00:00Z"),
                Instant.parse("2026-05-12T00:00:00Z"),
                null,
                null,
                "a2f8",
                PageRequest.of(0, 20));

        assertThat(page.getContent())
                .extracting(OrderDraft::getId)
                .containsExactly(farid.getId());
    }

    @Test
    void searchAdminQueryMatchesCustomerOrReferenceInsteadOfAndingThem() {
        OrderDraft farid = saveDraft("ET-2026-A2F8F0", "Farid Gomez", "2026-05-11T14:30:00Z");
        saveDraft("ET-2026-BBBBBB", "Ana Gomez", "2026-05-11T15:00:00Z");

        Page<OrderDraft> page = orderDraftRepository.searchAdmin(
                null,
                Instant.parse("2026-05-11T00:00:00Z"),
                Instant.parse("2026-05-12T00:00:00Z"),
                null,
                null,
                "ET-2026-A2F8F0",
                PageRequest.of(0, 20));

        assertThat(page.getContent())
                .extracting(OrderDraft::getId)
                .containsExactly(farid.getId());
    }

    @Test
    void searchAdminToDateIncludesEntireLocalDay() {
        OrderDraft farid = saveDraft("ET-2026-A2F8F0", "Farid Gomez", "2026-05-11T23:59:59Z");
        saveDraft("ET-2026-NEXTDY", "Next Day", "2026-05-12T00:00:00Z");

        Page<OrderDraft> page = orderDraftRepository.searchAdmin(
                null,
                Instant.parse("2026-05-11T00:00:00Z"),
                Instant.parse("2026-05-12T00:00:00Z"),
                null,
                null,
                "farid",
                PageRequest.of(0, 20));

        assertThat(page.getContent())
                .extracting(OrderDraft::getId)
                .containsExactly(farid.getId());
    }

    private OrderDraft saveDraft(String reference, String customerName, String createdAt) {
        OrderDraft draft = new OrderDraft();
        draft.setReference(reference);
        draft.setStatus(OrderDraftStatus.PAID);
        draft.setCustomerName(customerName);
        draft.setPhone("+5491112345678");
        draft.setCurrency("ARS");
        draft.setSubtotal(new BigDecimal("1000.00"));
        draft.setTotal(new BigDecimal("1000.00"));
        OrderDraft saved = orderDraftRepository.saveAndFlush(draft);
        ReflectionTestUtils.setField(saved, "createdAt", Instant.parse(createdAt));
        ReflectionTestUtils.setField(saved, "updatedAt", Instant.parse(createdAt));
        return orderDraftRepository.saveAndFlush(saved);
    }
}
