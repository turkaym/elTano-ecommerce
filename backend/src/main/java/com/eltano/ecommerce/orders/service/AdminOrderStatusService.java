package com.eltano.ecommerce.orders.service;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.eltano.ecommerce.common.api.ConflictException;
import com.eltano.ecommerce.common.api.ResourceNotFoundException;
import com.eltano.ecommerce.common.api.UnprocessableEntityException;
import com.eltano.ecommerce.orders.domain.OrderDraft;
import com.eltano.ecommerce.orders.domain.OrderDraftLine;
import com.eltano.ecommerce.orders.domain.OrderDraftStatus;
import com.eltano.ecommerce.orders.repository.OrderDraftRepository;

@Service
public class AdminOrderStatusService {

    private static final Map<OrderDraftStatus, Set<OrderDraftStatus>> ALLOWED_TRANSITIONS = Map.of(
            OrderDraftStatus.DRAFT, Set.of(OrderDraftStatus.CANCELLED, OrderDraftStatus.EXPIRED),
            OrderDraftStatus.PAYMENT_PENDING, Set.of(OrderDraftStatus.CANCELLED, OrderDraftStatus.EXPIRED),
            OrderDraftStatus.PAID, Set.of(OrderDraftStatus.PREPARING),
            OrderDraftStatus.PREPARING, Set.of(OrderDraftStatus.READY),
            OrderDraftStatus.READY, Set.of(OrderDraftStatus.DELIVERED));

    private final OrderDraftRepository orderDraftRepository;
    private final InventoryPolicyService inventoryPolicyService;


    public AdminOrderStatusService(
            OrderDraftRepository orderDraftRepository,
            InventoryPolicyService inventoryPolicyService) {
        this.orderDraftRepository = orderDraftRepository;
        this.inventoryPolicyService = inventoryPolicyService;
    }

    @Transactional
    public AdminOrderQueryService.OrderDetail updateStatus(UUID orderId, String requestedStatus) {
        OrderDraftStatus target = parseStatus(requestedStatus);
        OrderDraft draft = orderDraftRepository.findWithLinesById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        OrderDraftStatus current = draft.getStatus();
        if (current == target) {
            return toDetail(draft);
        }
        if (!ALLOWED_TRANSITIONS.getOrDefault(current, Set.of()).contains(target)) {
            throw new ConflictException("Invalid order status transition " + current + " -> " + target);
        }

        draft.setStatus(target);
        if (shouldReleaseStock(target) && draft.getStockReleasedAt() == null) {
            releaseReservedStock(draft);
            draft.setStockReleasedAt(Instant.now());
        }
        orderDraftRepository.save(draft);
        return toDetail(draft);
    }

    @Transactional
    public AdminOrderQueryService.OrderDetail updatePaymentStatus(UUID orderId, String requestedStatus) {
        OrderDraftStatus target = parseStatus(requestedStatus);
        if (target != OrderDraftStatus.PAID) {
            throw new UnprocessableEntityException("Unsupported payment status " + requestedStatus.trim());
        }

        OrderDraft draft = orderDraftRepository.findWithLinesById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        OrderDraftStatus current = draft.getStatus();
        if (current == OrderDraftStatus.PAID) {
            return toDetail(draft);
        }
        if (current != OrderDraftStatus.PAYMENT_PENDING && current != OrderDraftStatus.DRAFT) {
            throw new ConflictException("Invalid payment status transition " + current + " -> " + target);
        }

        draft.setStatus(OrderDraftStatus.PAID);
        if (draft.getPaymentProvider() == null || draft.getPaymentProvider().isBlank()) {
            draft.setPaymentProvider("manual");
        }
        draft.setPaymentStatusDetail("manual_paid");
        draft.setPaymentUpdatedAt(Instant.now());
        finalizeReservedStock(draft);
        orderDraftRepository.save(draft);
        return toDetail(draft);
    }

    private OrderDraftStatus parseStatus(String requestedStatus) {
        if (requestedStatus == null || requestedStatus.isBlank()) {
            throw new UnprocessableEntityException("Order status is required");
        }
        try {
            return OrderDraftStatus.valueOf(requestedStatus.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new UnprocessableEntityException("Unsupported order status " + requestedStatus.trim());
        }
    }

    private AdminOrderQueryService.OrderDetail toDetail(OrderDraft draft) {
        return new AdminOrderQueryService.OrderDetail(
                draft.getId(),
                draft.getReference(),
                draft.getStatus().name(),
                draft.getCustomerName(),
                draft.getPhone(),
                draft.getNote(),
                draft.getFulfillmentMethod().name(),
                draft.getDeliveryAddress(),
                draft.getPickupTime(),
                draft.getCurrency(),
                draft.getSubtotal(),
                draft.getTotal(),
                new AdminOrderQueryService.PaymentInfo(
                        draft.getPaymentProvider(),
                        draft.getPaymentPreferenceId(),
                        draft.getPaymentExternalId(),
                        draft.getPaymentStatusDetail(),
                        draft.getPaymentUpdatedAt()),
                draft.getLines().stream().map(this::toLineItem).toList(),
                draft.getCreatedAt(),
                draft.getUpdatedAt());
    }

    private boolean shouldReleaseStock(OrderDraftStatus status) {
        return status == OrderDraftStatus.FAILED
                || status == OrderDraftStatus.CANCELLED
                || status == OrderDraftStatus.EXPIRED;
    }

    private void releaseReservedStock(OrderDraft draft) {
        for (OrderDraftLine line : draft.getLines()) {
            inventoryPolicyService.release(line.getVariant(), line.getQuantity());
        }
    }

    private void finalizeReservedStock(OrderDraft draft) {
        for (OrderDraftLine line : draft.getLines()) {
            inventoryPolicyService.finalizeReservation(line.getVariant(), line.getQuantity());
        }
    }

    private AdminOrderQueryService.OrderLineItem toLineItem(OrderDraftLine line) {
        return new AdminOrderQueryService.OrderLineItem(
                line.getId(),
                line.getVariant().getId(),
                line.getProductName(),
                line.getUnitLabel(),
                line.getUnitPrice(),
                line.getQuantity(),
                line.getLineTotal());
    }
}
