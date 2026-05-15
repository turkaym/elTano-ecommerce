package com.eltano.ecommerce.orders.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.eltano.ecommerce.common.api.ConflictException;
import com.eltano.ecommerce.common.api.ResourceNotFoundException;
import com.eltano.ecommerce.common.api.UnprocessableEntityException;
import com.eltano.ecommerce.catalog.domain.ProductVariant;
import com.eltano.ecommerce.orders.domain.OrderDraft;
import com.eltano.ecommerce.orders.domain.OrderDraftLine;
import com.eltano.ecommerce.orders.domain.OrderDraftStatus;
import com.eltano.ecommerce.orders.repository.OrderDraftRepository;

@ExtendWith(MockitoExtension.class)
class AdminOrderStatusServiceTest {

    @Mock
    private OrderDraftRepository orderDraftRepository;

    @Mock
    private InventoryPolicyService inventoryPolicyService;

    @InjectMocks
    private AdminOrderStatusService adminOrderStatusService;

    @Test
    void updatesPaymentPendingOrderToCancelledAndReturnsDetail() {
        UUID orderId = UUID.fromString("de305d54-75b4-431b-adb2-eb6b9e546014");
        ProductVariant variant = variant(UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
        OrderDraft draft = draft(orderId, OrderDraftStatus.PAYMENT_PENDING);
        draft.addLine(line(variant, 2));
        when(orderDraftRepository.findWithLinesById(orderId)).thenReturn(Optional.of(draft));

        AdminOrderQueryService.OrderDetail detail = adminOrderStatusService.updateStatus(orderId, "CANCELLED");

        assertThat(detail.id()).isEqualTo(orderId);
        assertThat(detail.status()).isEqualTo("CANCELLED");
        assertThat(draft.getStatus()).isEqualTo(OrderDraftStatus.CANCELLED);
        assertThat(draft.getStockReleasedAt()).isNotNull();
        verify(inventoryPolicyService).release(variant, 2);
        verify(orderDraftRepository).save(draft);
    }

    @Test
    void manualPaymentConfirmationMarksPendingOrderPaidAndFinalizesReservedStock() {
        UUID orderId = UUID.fromString("44444444-4444-4444-4444-444444444444");
        ProductVariant variant = variant(UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"));
        OrderDraft draft = draft(orderId, OrderDraftStatus.PAYMENT_PENDING);
        draft.addLine(line(variant, 3));
        when(orderDraftRepository.findWithLinesById(orderId)).thenReturn(Optional.of(draft));

        AdminOrderQueryService.OrderDetail detail = adminOrderStatusService.updatePaymentStatus(orderId, "PAID");

        assertThat(detail.status()).isEqualTo("PAID");
        assertThat(draft.getStatus()).isEqualTo(OrderDraftStatus.PAID);
        assertThat(draft.getPaymentStatusDetail()).isEqualTo("manual_paid");
        assertThat(draft.getPaymentUpdatedAt()).isNotNull();
        verify(inventoryPolicyService).finalizeReservation(variant, 3);
        verify(orderDraftRepository).save(draft);
    }

    @Test
    void manualPaymentConfirmationMarksDraftOrderPaidAndFinalizesReservedStock() {
        UUID orderId = UUID.fromString("77777777-7777-7777-7777-777777777777");
        ProductVariant variant = variant(UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc"));
        OrderDraft draft = draft(orderId, OrderDraftStatus.DRAFT);
        draft.setPaymentProvider(null);
        draft.setPaymentStatusDetail(null);
        draft.addLine(line(variant, 2));
        when(orderDraftRepository.findWithLinesById(orderId)).thenReturn(Optional.of(draft));

        AdminOrderQueryService.OrderDetail detail = adminOrderStatusService.updatePaymentStatus(orderId, "PAID");

        assertThat(detail.status()).isEqualTo("PAID");
        assertThat(detail.payment().provider()).isEqualTo("manual");
        assertThat(detail.payment().statusDetail()).isEqualTo("manual_paid");
        assertThat(draft.getStatus()).isEqualTo(OrderDraftStatus.PAID);
        assertThat(draft.getPaymentProvider()).isEqualTo("manual");
        assertThat(draft.getPaymentStatusDetail()).isEqualTo("manual_paid");
        assertThat(draft.getPaymentUpdatedAt()).isNotNull();
        verify(inventoryPolicyService).finalizeReservation(variant, 2);
        verify(orderDraftRepository).save(draft);
    }

    @Test
    void allowsPaidOrderToProgressThroughOperationalFulfillmentStatuses() {
        UUID orderId = UUID.fromString("88888888-8888-8888-8888-888888888888");
        OrderDraft paid = draft(orderId, OrderDraftStatus.PAID);
        when(orderDraftRepository.findWithLinesById(orderId)).thenReturn(Optional.of(paid));

        AdminOrderQueryService.OrderDetail preparing = adminOrderStatusService.updateStatus(orderId, "PREPARING");

        assertThat(preparing.status()).isEqualTo("PREPARING");
        assertThat(paid.getStatus()).isEqualTo(OrderDraftStatus.PREPARING);
        verify(orderDraftRepository).save(paid);

        UUID preparingOrderId = UUID.fromString("99999999-9999-9999-9999-999999999999");
        OrderDraft preparingDraft = draft(preparingOrderId, OrderDraftStatus.PREPARING);
        when(orderDraftRepository.findWithLinesById(preparingOrderId)).thenReturn(Optional.of(preparingDraft));

        AdminOrderQueryService.OrderDetail ready = adminOrderStatusService.updateStatus(preparingOrderId, "READY");

        assertThat(ready.status()).isEqualTo("READY");
        assertThat(preparingDraft.getStatus()).isEqualTo(OrderDraftStatus.READY);
        verify(orderDraftRepository).save(preparingDraft);

        UUID readyOrderId = UUID.fromString("aaaaaaaa-1111-2222-3333-aaaaaaaaaaaa");
        OrderDraft readyDraft = draft(readyOrderId, OrderDraftStatus.READY);
        when(orderDraftRepository.findWithLinesById(readyOrderId)).thenReturn(Optional.of(readyDraft));

        AdminOrderQueryService.OrderDetail delivered = adminOrderStatusService.updateStatus(readyOrderId, "DELIVERED");

        assertThat(delivered.status()).isEqualTo("DELIVERED");
        assertThat(readyDraft.getStatus()).isEqualTo(OrderDraftStatus.DELIVERED);
        verify(orderDraftRepository).save(readyDraft);
    }

    @Test
    void rejectsInvalidOperationalTransitionWithConflict() {
        UUID orderId = UUID.fromString("bbbbbbbb-1111-2222-3333-bbbbbbbbbbbb");
        when(orderDraftRepository.findWithLinesById(orderId)).thenReturn(Optional.of(draft(orderId, OrderDraftStatus.PAID)));

        assertThatThrownBy(() -> adminOrderStatusService.updateStatus(orderId, "DELIVERED"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Invalid order status transition PAID -> DELIVERED");
    }

    @Test
    void rejectsTerminalOperationalOrderTransitionWithConflict() {
        UUID orderId = UUID.fromString("cccccccc-1111-2222-3333-cccccccccccc");
        when(orderDraftRepository.findWithLinesById(orderId)).thenReturn(Optional.of(draft(orderId, OrderDraftStatus.DELIVERED)));

        assertThatThrownBy(() -> adminOrderStatusService.updateStatus(orderId, "READY"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Invalid order status transition DELIVERED -> READY");
    }

    @Test
    void manualPaymentConfirmationRejectsAlreadyCancelledOrder() {
        UUID orderId = UUID.fromString("55555555-5555-5555-5555-555555555555");
        when(orderDraftRepository.findWithLinesById(orderId)).thenReturn(Optional.of(draft(orderId, OrderDraftStatus.CANCELLED)));

        assertThatThrownBy(() -> adminOrderStatusService.updatePaymentStatus(orderId, "PAID"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Invalid payment status transition CANCELLED -> PAID");
    }

    @Test
    void rejectsTerminalOrderTransitionWithConflict() {
        UUID orderId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        when(orderDraftRepository.findWithLinesById(orderId)).thenReturn(Optional.of(draft(orderId, OrderDraftStatus.PAID)));

        assertThatThrownBy(() -> adminOrderStatusService.updateStatus(orderId, "CANCELLED"))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Invalid order status transition PAID -> CANCELLED");
    }

    @Test
    void rejectsUnknownTargetStatusWithUnprocessableEntity() {
        UUID orderId = UUID.fromString("22222222-2222-2222-2222-222222222222");

        assertThatThrownBy(() -> adminOrderStatusService.updateStatus(orderId, "SHIPPED"))
                .isInstanceOf(UnprocessableEntityException.class)
                .hasMessageContaining("Unsupported order status SHIPPED");
    }

    @Test
    void returnsNotFoundForUnknownOrder() {
        UUID orderId = UUID.fromString("33333333-3333-3333-3333-333333333333");
        when(orderDraftRepository.findWithLinesById(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminOrderStatusService.updateStatus(orderId, "CANCELLED"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Order not found");
    }

    @Test
    void manualPaymentConfirmationReturnsNotFoundForUnknownOrder() {
        UUID orderId = UUID.fromString("66666666-6666-6666-6666-666666666666");
        when(orderDraftRepository.findWithLinesById(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminOrderStatusService.updatePaymentStatus(orderId, "PAID"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Order not found");
    }

    private OrderDraft draft(UUID orderId, OrderDraftStatus status) {
        OrderDraft draft = new OrderDraft();
        ReflectionTestUtils.setField(draft, "id", orderId);
        ReflectionTestUtils.setField(draft, "createdAt", Instant.parse("2026-05-01T09:00:00Z"));
        ReflectionTestUtils.setField(draft, "updatedAt", Instant.parse("2026-05-01T10:00:00Z"));
        draft.setReference("ET-2026-0007");
        draft.setStatus(status);
        draft.setCustomerName("Ana Gómez");
        draft.setPhone("11223344");
        draft.setCurrency("ARS");
        draft.setSubtotal(new BigDecimal("9000.00"));
        draft.setTotal(new BigDecimal("9500.00"));
        draft.setPaymentProvider("mercadopago");
        draft.setPaymentStatusDetail("pending");
        return draft;
    }

    private ProductVariant variant(UUID variantId) {
        ProductVariant variant = new ProductVariant();
        ReflectionTestUtils.setField(variant, "id", variantId);
        return variant;
    }

    private OrderDraftLine line(ProductVariant variant, int quantity) {
        OrderDraftLine line = new OrderDraftLine();
        ReflectionTestUtils.setField(line, "id", UUID.randomUUID());
        line.setVariant(variant);
        line.setProductName("Nuez");
        line.setUnitLabel("250g");
        line.setUnitPrice(new BigDecimal("1000.00"));
        line.setQuantity(quantity);
        line.setLineTotal(new BigDecimal("3000.00"));
        return line;
    }
}
