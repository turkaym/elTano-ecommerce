package com.eltano.ecommerce.orders.api;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.eltano.ecommerce.orders.api.dto.CreateOrderDraftRequest;
import com.eltano.ecommerce.orders.api.dto.CreateOrderDraftResponse;
import com.eltano.ecommerce.orders.api.dto.DraftPaymentStatusResponse;
import com.eltano.ecommerce.orders.api.dto.StartPaymentPreferenceResponse;
import com.eltano.ecommerce.orders.service.OrderDraftService;

import jakarta.validation.Valid;

@Validated
@RestController
@RequestMapping("/api/orders/drafts")
public class OrderDraftController {

    private final OrderDraftService orderDraftService;
    private final boolean mercadoPagoEnabled;

    public OrderDraftController(
            OrderDraftService orderDraftService,
            @Value("${app.mercadopago.enabled:false}") boolean mercadoPagoEnabled) {
        this.orderDraftService = orderDraftService;
        this.mercadoPagoEnabled = mercadoPagoEnabled;
    }

    @PostMapping
    public ResponseEntity<CreateOrderDraftResponse> createDraft(@Valid @RequestBody CreateOrderDraftRequest request) {
        OrderDraftService.Result result = orderDraftService.createDraft(new OrderDraftService.Command(
                request.customerName(),
                request.phone(),
                request.note(),
                request.items().stream()
                        .map(item -> new OrderDraftService.CommandItem(item.variantId(), item.quantity()))
                        .toList()));

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new CreateOrderDraftResponse(
                        result.draftId(),
                        result.reference(),
                        result.currency(),
                        result.subtotal(),
                        result.total(),
                        result.whatsappMessage()));
    }

    @PostMapping("/{draftId}/payment-preference")
    public ResponseEntity<StartPaymentPreferenceResponse> startPaymentPreference(@PathVariable UUID draftId) {
        if (!mercadoPagoEnabled) {
            throw new IllegalStateException("Mercado Pago checkout is disabled");
        }

        OrderDraftService.PaymentPreferenceResult result = orderDraftService
                .startPaymentPreference(new OrderDraftService.StartPaymentPreferenceCommand(draftId));
        return ResponseEntity.ok(new StartPaymentPreferenceResponse(
                result.draftId(),
                result.preferenceId(),
                result.initPoint()));
    }

    @GetMapping("/{draftId}/payment-status")
    public ResponseEntity<DraftPaymentStatusResponse> getPaymentStatus(@PathVariable UUID draftId) {
        OrderDraftService.PaymentStatusResult result = orderDraftService.getPaymentStatus(draftId);
        return ResponseEntity.ok(new DraftPaymentStatusResponse(
                result.draftId(),
                result.reference(),
                result.status(),
                result.updatedAt(),
                result.canRetry()));
    }
}
