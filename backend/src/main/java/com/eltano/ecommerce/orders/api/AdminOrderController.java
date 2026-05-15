package com.eltano.ecommerce.orders.api;

import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.eltano.ecommerce.orders.api.dto.AdminOrderStatusUpdateRequest;
import com.eltano.ecommerce.orders.api.dto.AdminOrderDetailResponse;
import com.eltano.ecommerce.orders.api.dto.AdminOrderListResponse;
import com.eltano.ecommerce.orders.service.AdminOrderQueryService;
import com.eltano.ecommerce.orders.service.AdminOrderStatusService;

import jakarta.validation.Valid;

@Validated
@RestController
@RequestMapping("/api/admin/orders")
public class AdminOrderController {

    private final AdminOrderQueryService adminOrderQueryService;
    private final AdminOrderStatusService adminOrderStatusService;

    public AdminOrderController(
            AdminOrderQueryService adminOrderQueryService,
            AdminOrderStatusService adminOrderStatusService) {
        this.adminOrderQueryService = adminOrderQueryService;
        this.adminOrderStatusService = adminOrderStatusService;
    }

    @GetMapping
    public ResponseEntity<AdminOrderListResponse> list(
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "from", required = false) String from,
            @RequestParam(name = "to", required = false) String to,
            @RequestParam(name = "customer", required = false) String customer,
            @RequestParam(name = "reference", required = false) String reference,
            @RequestParam(name = "query", required = false) String query,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        AdminOrderQueryService.OrderListResult result = adminOrderQueryService.listOrders(
                status,
                from,
                to,
                customer,
                reference,
                query,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));

        AdminOrderListResponse response = new AdminOrderListResponse(
                result.items().stream()
                        .map(item -> new AdminOrderListResponse.Item(
                                item.id(),
                                item.reference(),
                                item.status(),
                                item.customer(),
                                item.paymentStatus(),
                                item.total(),
                                item.createdAt()))
                        .toList(),
                result.page(),
                result.size(),
                result.totalElements(),
                result.totalPages());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<AdminOrderDetailResponse> detail(@PathVariable UUID id) {
        AdminOrderQueryService.OrderDetail detail = adminOrderQueryService.getOrder(id);
        return ResponseEntity.ok(toDetailResponse(detail));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<AdminOrderDetailResponse> updateStatus(
            @PathVariable UUID id,
            @Valid @RequestBody AdminOrderStatusUpdateRequest request) {
        AdminOrderQueryService.OrderDetail detail = adminOrderStatusService.updateStatus(id, request.status());
        return ResponseEntity.ok(toDetailResponse(detail));
    }

    @PatchMapping("/{id}/payment-status")
    public ResponseEntity<AdminOrderDetailResponse> updatePaymentStatus(
            @PathVariable UUID id,
            @Valid @RequestBody AdminOrderStatusUpdateRequest request) {
        AdminOrderQueryService.OrderDetail detail = adminOrderStatusService.updatePaymentStatus(id, request.status());
        return ResponseEntity.ok(toDetailResponse(detail));
    }

    private AdminOrderDetailResponse toDetailResponse(AdminOrderQueryService.OrderDetail detail) {
        return new AdminOrderDetailResponse(
                detail.id(),
                detail.reference(),
                detail.status(),
                detail.customer(),
                detail.phone(),
                detail.note(),
                detail.currency(),
                detail.subtotal(),
                detail.total(),
                new AdminOrderDetailResponse.Payment(
                        detail.payment().provider(),
                        detail.payment().preferenceId(),
                        detail.payment().externalId(),
                        detail.payment().statusDetail(),
                        detail.payment().updatedAt()),
                detail.items().stream()
                        .map(item -> new AdminOrderDetailResponse.Item(
                                item.id(),
                                item.variantId(),
                                item.productName(),
                                item.unitLabel(),
                                item.unitPrice(),
                                item.quantity(),
                                item.subtotal()))
                        .toList(),
                detail.createdAt(),
                detail.updatedAt());
    }
}
