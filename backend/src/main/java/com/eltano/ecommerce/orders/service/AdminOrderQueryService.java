package com.eltano.ecommerce.orders.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.eltano.ecommerce.common.api.ResourceNotFoundException;
import com.eltano.ecommerce.orders.domain.OrderDraft;
import com.eltano.ecommerce.orders.domain.OrderDraftStatus;
import com.eltano.ecommerce.orders.repository.OrderDraftRepository;

@Service
public class AdminOrderQueryService {

    private final OrderDraftRepository orderDraftRepository;

    public AdminOrderQueryService(OrderDraftRepository orderDraftRepository) {
        this.orderDraftRepository = orderDraftRepository;
    }

    @Transactional(readOnly = true)
    public OrderListResult listOrders(
            String status,
            String from,
            String to,
            String customer,
            String reference,
            Pageable pageable) {
        OrderDraftStatus parsedStatus = parseStatus(status);
        Instant fromInstant = parseFrom(from);
        Instant toInstantExclusive = parseToExclusive(to);

        Page<OrderDraft> page = orderDraftRepository.searchAdmin(
                parsedStatus,
                fromInstant,
                toInstantExclusive,
                normalize(customer),
                normalize(reference),
                pageable);

        return new OrderListResult(
                page.getContent().stream().map(this::toListItem).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }

    @Transactional(readOnly = true)
    public OrderDetail getOrder(UUID orderId) {
        OrderDraft draft = orderDraftRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order not found"));

        return new OrderDetail(
                draft.getId(),
                draft.getReference(),
                draft.getStatus().name(),
                draft.getCustomerName(),
                draft.getPhone(),
                draft.getNote(),
                draft.getCurrency(),
                draft.getSubtotal(),
                draft.getTotal(),
                draft.getCreatedAt(),
                draft.getUpdatedAt());
    }

    private OrderListItem toListItem(OrderDraft draft) {
        return new OrderListItem(
                draft.getId(),
                draft.getReference(),
                draft.getStatus().name(),
                draft.getCustomerName(),
                draft.getTotal(),
                draft.getCreatedAt());
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private OrderDraftStatus parseStatus(String status) {
        String normalized = normalize(status);
        if (normalized == null) {
            return null;
        }
        return OrderDraftStatus.valueOf(normalized);
    }

    private Instant parseFrom(String from) {
        String normalized = normalize(from);
        if (normalized == null) {
            return null;
        }
        return LocalDate.parse(normalized).atStartOfDay().toInstant(ZoneOffset.UTC);
    }

    private Instant parseToExclusive(String to) {
        String normalized = normalize(to);
        if (normalized == null) {
            return null;
        }
        return LocalDate.parse(normalized).plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC);
    }

    public record OrderListItem(
            UUID id,
            String reference,
            String status,
            String customer,
            BigDecimal total,
            Instant createdAt) {
    }

    public record OrderListResult(
            java.util.List<OrderListItem> items,
            int page,
            int size,
            long totalElements,
            int totalPages) {
    }

    public record OrderDetail(
            UUID id,
            String reference,
            String status,
            String customer,
            String phone,
            String note,
            String currency,
            BigDecimal subtotal,
            BigDecimal total,
            Instant createdAt,
            Instant updatedAt) {
    }
}
