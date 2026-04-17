package com.eltano.ecommerce.orders.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.Year;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.eltano.ecommerce.catalog.domain.ProductVariant;
import com.eltano.ecommerce.catalog.repository.ProductVariantRepository;
import com.eltano.ecommerce.common.api.ConflictException;
import com.eltano.ecommerce.common.api.ResourceNotFoundException;
import com.eltano.ecommerce.orders.domain.OrderDraft;
import com.eltano.ecommerce.orders.domain.OrderDraftLine;
import com.eltano.ecommerce.orders.domain.OrderDraftStatus;
import com.eltano.ecommerce.orders.payment.mercadopago.MercadoPagoClient;
import com.eltano.ecommerce.orders.repository.OrderDraftRepository;

@Service
public class OrderDraftService {

    private static final String CURRENCY = "ARS";
    private static final String REFERENCE_PREFIX = "ET";
    private static final Set<OrderDraftStatus> STARTABLE_PAYMENT_STATES = Set.of(
            OrderDraftStatus.DRAFT,
            OrderDraftStatus.PAYMENT_PENDING);
    private static final Set<OrderDraftStatus> RETRYABLE_STATUSES = Set.of(
            OrderDraftStatus.FAILED,
            OrderDraftStatus.CANCELLED,
            OrderDraftStatus.EXPIRED);

    private final ProductVariantRepository productVariantRepository;
    private final OrderDraftRepository orderDraftRepository;
    private final MercadoPagoClient mercadoPagoClient;

    public OrderDraftService(
            ProductVariantRepository productVariantRepository,
            OrderDraftRepository orderDraftRepository,
            MercadoPagoClient mercadoPagoClient) {
        this.productVariantRepository = productVariantRepository;
        this.orderDraftRepository = orderDraftRepository;
        this.mercadoPagoClient = mercadoPagoClient;
    }

    @Transactional
    public Result createDraft(Command command) {
        List<UUID> variantIds = command.items().stream()
                .map(CommandItem::variantId)
                .toList();

        List<ProductVariant> lockedVariants = productVariantRepository.findAllByIdInForUpdate(variantIds);
        Map<UUID, ProductVariant> variantsById = new HashMap<>();
        for (ProductVariant variant : lockedVariants) {
            variantsById.put(variant.getId(), variant);
        }

        OrderDraft draft = new OrderDraft();
        draft.setReference(nextReference());
        draft.setStatus(OrderDraftStatus.DRAFT);
        draft.setCustomerName(command.customerName().trim());
        draft.setPhone(command.phone().trim());
        draft.setNote(command.note() == null ? null : command.note().trim());
        draft.setCurrency(CURRENCY);

        BigDecimal subtotal = BigDecimal.ZERO;
        for (CommandItem commandItem : command.items()) {
            ProductVariant variant = variantsById.get(commandItem.variantId());
            if (variant == null) {
                throw new IllegalArgumentException("Variant not found");
            }
            if (!variant.isActive()) {
                throw new IllegalArgumentException("Variant is inactive");
            }
            if (variant.getStockAvailable() < commandItem.quantity()) {
                throw new ConflictException("Insufficient stock for variant " + variant.getId());
            }

            variant.setStockAvailable(variant.getStockAvailable() - commandItem.quantity());
            variant.setStockReserved(variant.getStockReserved() + commandItem.quantity());

            BigDecimal lineTotal = variant.getPrice().multiply(BigDecimal.valueOf(commandItem.quantity()));
            subtotal = subtotal.add(lineTotal);

            OrderDraftLine line = new OrderDraftLine();
            line.setVariant(variant);
            line.setProductName(variant.getProduct().getName());
            line.setUnitLabel(variant.getUnitLabel());
            line.setUnitPrice(variant.getPrice());
            line.setQuantity(commandItem.quantity());
            line.setLineTotal(lineTotal);
            draft.addLine(line);
        }

        BigDecimal roundedSubtotal = subtotal.setScale(2, RoundingMode.HALF_UP);
        draft.setSubtotal(roundedSubtotal);
        draft.setTotal(roundedSubtotal);

        OrderDraft saved = orderDraftRepository.save(draft);
        return new Result(
                saved.getId(),
                saved.getReference(),
                saved.getCurrency(),
                saved.getSubtotal(),
                saved.getTotal(),
                buildWhatsappMessage(saved));
    }

    private String nextReference() {
        String year = String.valueOf(Year.now().getValue());
        String reference;
        do {
            String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
            reference = REFERENCE_PREFIX + "-" + year + "-" + suffix;
        } while (orderDraftRepository.existsByReference(reference));
        return reference;
    }

    private String buildWhatsappMessage(OrderDraft draft) {
        StringBuilder lines = new StringBuilder();
        lines.append("Hola, confirmo el pedido ").append(draft.getReference()).append(".\n");
        for (OrderDraftLine line : draft.getLines()) {
            lines.append("- ")
                    .append(line.getProductName())
                    .append(" x")
                    .append(line.getQuantity())
                    .append(" (")
                    .append(line.getUnitLabel())
                    .append(")\n");
        }
        lines.append("Total ").append(draft.getCurrency()).append(" ").append(draft.getTotal());
        return lines.toString();
    }

    @Transactional
    public PaymentPreferenceResult startPayment(StartPaymentCommand command) {
        OrderDraft draft = orderDraftRepository.findByIdForUpdate(command.draftId())
                .orElseThrow(() -> new ResourceNotFoundException("Draft not found"));

        if (!STARTABLE_PAYMENT_STATES.contains(draft.getStatus())) {
            throw new IllegalStateException("Draft cannot start payment from status " + draft.getStatus());
        }

        draft.setStatus(OrderDraftStatus.PAYMENT_PENDING);
        draft.setPaymentProvider("mercadopago");
        draft.setPaymentPreferenceId(command.preferenceId());
        draft.setPaymentUpdatedAt(Instant.now());
        orderDraftRepository.save(draft);

        return new PaymentPreferenceResult(draft.getId(), command.preferenceId(), command.initPoint());
    }

    @Transactional
    public PaymentPreferenceResult startPaymentPreference(StartPaymentPreferenceCommand command) {
        OrderDraft draft = orderDraftRepository.findById(command.draftId())
                .orElseThrow(() -> new ResourceNotFoundException("Draft not found"));
        MercadoPagoClient.Preference preference = mercadoPagoClient.createPreference(
                draft.getId(),
                draft.getReference(),
                draft.getCurrency(),
                draft.getTotal());
        return startPayment(new StartPaymentCommand(draft.getId(), preference.preferenceId(), preference.initPoint()));
    }

    @Transactional(readOnly = true)
    public PaymentStatusResult getPaymentStatus(UUID draftId) {
        OrderDraft draft = orderDraftRepository.findById(draftId)
                .orElseThrow(() -> new ResourceNotFoundException("Draft not found"));

        Instant updatedAt = draft.getPaymentUpdatedAt() != null ? draft.getPaymentUpdatedAt() : draft.getUpdatedAt();
        return new PaymentStatusResult(
                draft.getId(),
                draft.getReference(),
                draft.getStatus().name(),
                updatedAt != null ? updatedAt.toString() : Instant.now().toString(),
                RETRYABLE_STATUSES.contains(draft.getStatus()));
    }

    @Transactional
    public PaymentTransitionResult applyPaymentTransition(PaymentTransitionCommand command) {
        OrderDraft draft = orderDraftRepository.findByIdForUpdate(command.draftId())
                .orElseThrow(() -> new ResourceNotFoundException("Draft not found"));

        OrderDraftStatus current = draft.getStatus();
        OrderDraftStatus target = command.status();

        if (current == target) {
            draft.setPaymentStatusDetail(command.statusDetail());
            draft.setPaymentExternalId(command.paymentExternalId());
            draft.setPaymentUpdatedAt(Instant.now());
            orderDraftRepository.save(draft);
            return new PaymentTransitionResult(draft.getId(), "IDEMPOTENT_SAME_STATE");
        }

        if (isTerminal(current)) {
            return new PaymentTransitionResult(draft.getId(), "REGRESSION_IGNORED");
        }

        if (current != OrderDraftStatus.PAYMENT_PENDING || !isTerminal(target)) {
            throw new IllegalStateException("Invalid transition " + current + " -> " + target);
        }

        draft.setStatus(target);
        draft.setPaymentExternalId(command.paymentExternalId());
        draft.setPaymentStatusDetail(command.statusDetail());
        draft.setPaymentUpdatedAt(Instant.now());

        if (shouldReleaseStock(target) && draft.getStockReleasedAt() == null) {
            releaseReservedStock(draft);
            draft.setStockReleasedAt(Instant.now());
        }

        orderDraftRepository.save(draft);
        return new PaymentTransitionResult(draft.getId(), target.name() + "_APPLIED");
    }

    private boolean isTerminal(OrderDraftStatus status) {
        return status == OrderDraftStatus.PAID
                || status == OrderDraftStatus.FAILED
                || status == OrderDraftStatus.CANCELLED
                || status == OrderDraftStatus.EXPIRED;
    }

    private boolean shouldReleaseStock(OrderDraftStatus status) {
        return status == OrderDraftStatus.FAILED
                || status == OrderDraftStatus.CANCELLED
                || status == OrderDraftStatus.EXPIRED;
    }

    private void releaseReservedStock(OrderDraft draft) {
        for (OrderDraftLine line : draft.getLines()) {
            ProductVariant variant = line.getVariant();
            int quantity = line.getQuantity();
            variant.setStockReserved(Math.max(0, variant.getStockReserved() - quantity));
            variant.setStockAvailable(variant.getStockAvailable() + quantity);
        }
    }

    public record Command(
            String customerName,
            String phone,
            String note,
            List<CommandItem> items) {
    }

    public record CommandItem(UUID variantId, int quantity) {
    }

    public record Result(
            UUID draftId,
            String reference,
            String currency,
            BigDecimal subtotal,
            BigDecimal total,
            String whatsappMessage) {
    }

    public record StartPaymentCommand(UUID draftId, String preferenceId, String initPoint) {
    }

    public record StartPaymentPreferenceCommand(UUID draftId) {
    }

    public record PaymentPreferenceResult(UUID draftId, String preferenceId, String initPoint) {
    }

    public record PaymentStatusResult(
            UUID draftId,
            String reference,
            String status,
            String updatedAt,
            boolean canRetry) {
    }

    public record PaymentTransitionCommand(
            UUID draftId,
            String paymentExternalId,
            OrderDraftStatus status,
            String statusDetail) {
    }

    public record PaymentTransitionResult(UUID draftId, String outcome) {
    }
}
