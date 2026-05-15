package com.eltano.ecommerce.orders.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import com.eltano.ecommerce.catalog.domain.ProductVariant;
import com.eltano.ecommerce.orders.domain.OrderDraft;
import com.eltano.ecommerce.orders.domain.OrderDraftLine;
import com.eltano.ecommerce.orders.domain.OrderDraftStatus;
import com.eltano.ecommerce.orders.repository.OrderDraftRepository;

@ExtendWith(MockitoExtension.class)
class AdminOrderQueryServiceTest {

    @Mock
    private OrderDraftRepository orderDraftRepository;

    @InjectMocks
    private AdminOrderQueryService adminOrderQueryService;

    @Test
    void listUsesBuenosAiresDateBoundariesAndQueryFilter() {
        PageRequest pageable = PageRequest.of(0, 20);
        when(orderDraftRepository.searchAdmin(
                isNull(),
                eq(Instant.parse("2026-05-11T03:00:00Z")),
                eq(Instant.parse("2026-05-12T03:00:00Z")),
                isNull(),
                isNull(),
                eq("farid"),
                eq(pageable)))
                .thenReturn(new PageImpl<>(java.util.List.of(), pageable, 0));

        adminOrderQueryService.listOrders(null, "2026-05-11", "2026-05-11", null, null, " farid ", pageable);

        ArgumentCaptor<Instant> fromCaptor = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> toCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(orderDraftRepository).searchAdmin(
                isNull(),
                fromCaptor.capture(),
                toCaptor.capture(),
                isNull(),
                isNull(),
                eq("farid"),
                eq(pageable));
        assertThat(fromCaptor.getValue()).isEqualTo(Instant.parse("2026-05-11T03:00:00Z"));
        assertThat(toCaptor.getValue()).isEqualTo(Instant.parse("2026-05-12T03:00:00Z"));
    }

    @Test
    void detailIncludesPaymentMetadataAndLineItems() {
        UUID orderId = UUID.fromString("de305d54-75b4-431b-adb2-eb6b9e546014");
        UUID lineId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        UUID variantId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        OrderDraft draft = new OrderDraft();
        ReflectionTestUtils.setField(draft, "id", orderId);
        ReflectionTestUtils.setField(draft, "createdAt", Instant.parse("2026-05-01T09:00:00Z"));
        ReflectionTestUtils.setField(draft, "updatedAt", Instant.parse("2026-05-01T10:00:00Z"));
        draft.setReference("ET-2026-0007");
        draft.setStatus(OrderDraftStatus.PAID);
        draft.setCustomerName("Ana Gómez");
        draft.setPhone("11223344");
        draft.setNote("Tocar timbre");
        draft.setCurrency("ARS");
        draft.setSubtotal(new BigDecimal("9000.00"));
        draft.setTotal(new BigDecimal("9500.00"));
        draft.setPaymentProvider("mercadopago");
        draft.setPaymentPreferenceId("pref-1");
        draft.setPaymentExternalId("pay-1");
        draft.setPaymentStatusDetail("approved");
        draft.setPaymentUpdatedAt(Instant.parse("2026-05-01T10:00:00Z"));

        ProductVariant variant = new ProductVariant();
        ReflectionTestUtils.setField(variant, "id", variantId);
        OrderDraftLine line = new OrderDraftLine();
        ReflectionTestUtils.setField(line, "id", lineId);
        line.setVariant(variant);
        line.setProductName("Nuez");
        line.setUnitLabel("250g");
        line.setUnitPrice(new BigDecimal("4500.00"));
        line.setQuantity(2);
        line.setLineTotal(new BigDecimal("9000.00"));
        draft.addLine(line);
        when(orderDraftRepository.findWithLinesById(orderId)).thenReturn(Optional.of(draft));

        AdminOrderQueryService.OrderDetail detail = adminOrderQueryService.getOrder(orderId);

        assertThat(detail.reference()).isEqualTo("ET-2026-0007");
        assertThat(detail.payment().provider()).isEqualTo("mercadopago");
        assertThat(detail.payment().statusDetail()).isEqualTo("approved");
        assertThat(detail.items()).hasSize(1);
        assertThat(detail.items().getFirst().variantId()).isEqualTo(variantId);
        assertThat(detail.items().getFirst().productName()).isEqualTo("Nuez");
        assertThat(detail.items().getFirst().quantity()).isEqualTo(2);
        assertThat(detail.items().getFirst().subtotal()).isEqualByComparingTo("9000.00");
    }
}
