package com.eltano.ecommerce.orders.api;

import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.eltano.ecommerce.orders.api.dto.AdminOrderDetailResponse;
import com.eltano.ecommerce.orders.api.dto.AdminOrderListResponse;
import com.eltano.ecommerce.orders.service.AdminOrderQueryService;

@Validated
@RestController
@RequestMapping("/api/admin/orders")
public class AdminOrderController {

    private final AdminOrderQueryService adminOrderQueryService;

    public AdminOrderController(AdminOrderQueryService adminOrderQueryService) {
        this.adminOrderQueryService = adminOrderQueryService;
    }

    @GetMapping
    public ResponseEntity<AdminOrderListResponse> list(
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "from", required = false) String from,
            @RequestParam(name = "to", required = false) String to,
            @RequestParam(name = "customer", required = false) String customer,
            @RequestParam(name = "reference", required = false) String reference,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {
        AdminOrderQueryService.OrderListResult result = adminOrderQueryService.listOrders(
                status,
                from,
                to,
                customer,
                reference,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));

        AdminOrderListResponse response = new AdminOrderListResponse(
                result.items().stream()
                        .map(item -> new AdminOrderListResponse.Item(
                                item.id(),
                                item.reference(),
                                item.status(),
                                item.customer(),
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
        return ResponseEntity.ok(new AdminOrderDetailResponse(
                detail.id(),
                detail.reference(),
                detail.status(),
                detail.customer(),
                detail.phone(),
                detail.note(),
                detail.currency(),
                detail.subtotal(),
                detail.total(),
                detail.createdAt(),
                detail.updatedAt()));
    }
}
