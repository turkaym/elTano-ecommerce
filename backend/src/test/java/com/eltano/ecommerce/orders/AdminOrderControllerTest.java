package com.eltano.ecommerce.orders;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.eltano.ecommerce.common.api.ConflictException;
import com.eltano.ecommerce.common.api.ResourceNotFoundException;
import com.eltano.ecommerce.common.api.RestExceptionHandler;
import com.eltano.ecommerce.common.api.UnprocessableEntityException;
import com.eltano.ecommerce.config.SecurityConfig;
import com.eltano.ecommerce.catalog.config.ProductImageUploadProperties;
import com.eltano.ecommerce.orders.api.AdminOrderController;
import com.eltano.ecommerce.audit.service.AdminAuditService;
import com.eltano.ecommerce.orders.service.AdminOrderQueryService;
import com.eltano.ecommerce.orders.service.AdminOrderStatusService;

@WebMvcTest(controllers = AdminOrderController.class)
@Import({ SecurityConfig.class, RestExceptionHandler.class, AdminOrderControllerTest.TestUploadConfig.class })
class AdminOrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminOrderQueryService adminOrderQueryService;

    @MockBean
    private AdminOrderStatusService adminOrderStatusService;

    @MockBean
    private AdminAuditService adminAuditService;

    @TestConfiguration
    static class TestUploadConfig {
        @Bean
        ProductImageUploadProperties productImageUploadProperties() {
            ProductImageUploadProperties properties = new ProductImageUploadProperties();
            properties.setPublicPath("/uploads/product-images");
            properties.setDirectory(Path.of("build", "test-uploads"));
            return properties;
        }
    }

    @Test
    void listSupportsFiltersAndPagingMetadata() throws Exception {
        AdminOrderQueryService.OrderListItem item = new AdminOrderQueryService.OrderListItem(
                UUID.fromString("de305d54-75b4-431b-adb2-eb6b9e546014"),
                "ET-2026-ABC123",
                "DRAFT",
                "Juan Perez",
                "pending",
                new BigDecimal("12000.00"),
                Instant.parse("2026-04-01T10:15:30Z"));

        AdminOrderQueryService.OrderListResult result = new AdminOrderQueryService.OrderListResult(
                List.of(item),
                1,
                10,
                1,
                1);

        when(adminOrderQueryService.listOrders(
                eq("DRAFT"),
                eq("2026-04-01"),
                eq("2026-04-30"),
                eq("juan"),
                eq("ET-2026"),
                eq("juan or ET-2026"),
                eq(PageRequest.of(1, 10, Sort.by(Sort.Direction.DESC, "createdAt")))))
                .thenReturn(result);

        mockMvc.perform(get("/api/admin/orders")
                .param("status", "DRAFT")
                .param("from", "2026-04-01")
                .param("to", "2026-04-30")
                .param("customer", "juan")
                .param("reference", "ET-2026")
                .param("query", "juan or ET-2026")
                .param("page", "1")
                .param("size", "10")
                .with(SecurityMockMvcRequestPostProcessors.httpBasic("admin-user", "admin-pass")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].reference").value("ET-2026-ABC123"))
                .andExpect(jsonPath("$.items[0].status").value("DRAFT"))
                .andExpect(jsonPath("$.items[0].paymentStatus").value("pending"))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void detailReturnsCustomerPaymentAndLineItems() throws Exception {
        UUID orderId = UUID.fromString("de305d54-75b4-431b-adb2-eb6b9e546014");
        UUID lineId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        UUID variantId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        AdminOrderQueryService.OrderDetail detail = new AdminOrderQueryService.OrderDetail(
                orderId,
                "ET-2026-0007",
                "PAID",
                "Ana Gómez",
                "11223344",
                "Tocar timbre",
                "ARS",
                new BigDecimal("9000.00"),
                new BigDecimal("9500.00"),
                new AdminOrderQueryService.PaymentInfo(
                        "mercadopago",
                        "pref-1",
                        "pay-1",
                        "approved",
                        Instant.parse("2026-05-01T10:00:00Z")),
                List.of(new AdminOrderQueryService.OrderLineItem(
                        lineId,
                        variantId,
                        "Nuez",
                        "250g",
                        new BigDecimal("4500.00"),
                        2,
                        new BigDecimal("9000.00"))),
                Instant.parse("2026-05-01T09:00:00Z"),
                Instant.parse("2026-05-01T10:00:00Z"));
        when(adminOrderQueryService.getOrder(orderId)).thenReturn(detail);

        mockMvc.perform(get("/api/admin/orders/{id}", orderId)
                .with(SecurityMockMvcRequestPostProcessors.httpBasic("admin-user", "admin-pass")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reference").value("ET-2026-0007"))
                .andExpect(jsonPath("$.customer").value("Ana Gómez"))
                .andExpect(jsonPath("$.payment.provider").value("mercadopago"))
                .andExpect(jsonPath("$.payment.statusDetail").value("approved"))
                .andExpect(jsonPath("$.items[0].variantId").value(variantId.toString()))
                .andExpect(jsonPath("$.items[0].productName").value("Nuez"))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.items[0].subtotal").value(9000.00));
    }

    @Test
    void detailReturns404WithStandardAdminErrorSchema() throws Exception {
        UUID orderId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        when(adminOrderQueryService.getOrder(orderId)).thenThrow(new ResourceNotFoundException("Order not found"));

        mockMvc.perform(get("/api/admin/orders/{id}", orderId)
                .with(SecurityMockMvcRequestPostProcessors.httpBasic("admin-user", "admin-pass")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Order not found"))
                .andExpect(jsonPath("$.correlationId").isString())
                .andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.fieldErrors").isEmpty());
    }

    @Test
    void patchStatusRequiresCsrfForAdminWrite() throws Exception {
        UUID orderId = UUID.fromString("de305d54-75b4-431b-adb2-eb6b9e546014");

        mockMvc.perform(patch("/api/admin/orders/{id}/status", orderId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"CANCELLED\"}")
                .with(SecurityMockMvcRequestPostProcessors.httpBasic("admin-user", "admin-pass")))
                .andExpect(status().isForbidden());
    }

    @Test
    void patchStatusReturnsUpdatedDetailAndUsesAdminAuditInterceptor() throws Exception {
        UUID orderId = UUID.fromString("de305d54-75b4-431b-adb2-eb6b9e546014");
        AdminOrderQueryService.OrderDetail detail = detail(orderId, "CANCELLED");
        when(adminOrderStatusService.updateStatus(orderId, "CANCELLED")).thenReturn(detail);

        mockMvc.perform(patch("/api/admin/orders/{id}/status", orderId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"CANCELLED\"}")
                .with(SecurityMockMvcRequestPostProcessors.httpBasic("admin-user", "admin-pass"))
                .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId.toString()))
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.reference").value("ET-2026-0007"));

        verify(adminOrderStatusService).updateStatus(orderId, "CANCELLED");
    }

    @Test
    void patchPaymentStatusRequiresCsrfForAdminWrite() throws Exception {
        UUID orderId = UUID.fromString("de305d54-75b4-431b-adb2-eb6b9e546014");

        mockMvc.perform(patch("/api/admin/orders/{id}/payment-status", orderId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"PAID\"}")
                .with(SecurityMockMvcRequestPostProcessors.httpBasic("admin-user", "admin-pass")))
                .andExpect(status().isForbidden());
    }

    @Test
    void patchPaymentStatusReturnsUpdatedPaidDetail() throws Exception {
        UUID orderId = UUID.fromString("de305d54-75b4-431b-adb2-eb6b9e546014");
        AdminOrderQueryService.OrderDetail detail = detail(orderId, "PAID");
        when(adminOrderStatusService.updatePaymentStatus(orderId, "PAID")).thenReturn(detail);

        mockMvc.perform(patch("/api/admin/orders/{id}/payment-status", orderId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"PAID\"}")
                .with(SecurityMockMvcRequestPostProcessors.httpBasic("admin-user", "admin-pass"))
                .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId.toString()))
                .andExpect(jsonPath("$.status").value("PAID"));

        verify(adminOrderStatusService).updatePaymentStatus(orderId, "PAID");
    }

    @Test
    void patchPaymentStatusReturns409ForInvalidPaymentTransition() throws Exception {
        UUID orderId = UUID.fromString("de305d54-75b4-431b-adb2-eb6b9e546014");
        when(adminOrderStatusService.updatePaymentStatus(orderId, "PAID"))
                .thenThrow(new ConflictException("Invalid payment status transition CANCELLED -> PAID"));

        mockMvc.perform(patch("/api/admin/orders/{id}/payment-status", orderId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"PAID\"}")
                .with(SecurityMockMvcRequestPostProcessors.httpBasic("admin-user", "admin-pass"))
                .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value("Invalid payment status transition CANCELLED -> PAID"));
    }

    @Test
    void patchPaymentStatusReturns404ForUnknownOrder() throws Exception {
        UUID orderId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        when(adminOrderStatusService.updatePaymentStatus(orderId, "PAID"))
                .thenThrow(new ResourceNotFoundException("Order not found"));

        mockMvc.perform(patch("/api/admin/orders/{id}/payment-status", orderId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"PAID\"}")
                .with(SecurityMockMvcRequestPostProcessors.httpBasic("admin-user", "admin-pass"))
                .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Order not found"));
    }

    @Test
    void patchStatusReturns422ForUnsupportedStatus() throws Exception {
        UUID orderId = UUID.fromString("de305d54-75b4-431b-adb2-eb6b9e546014");
        when(adminOrderStatusService.updateStatus(orderId, "SHIPPED"))
                .thenThrow(new UnprocessableEntityException("Unsupported order status SHIPPED"));

        mockMvc.perform(patch("/api/admin/orders/{id}/status", orderId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"SHIPPED\"}")
                .with(SecurityMockMvcRequestPostProcessors.httpBasic("admin-user", "admin-pass"))
                .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("UNPROCESSABLE_ENTITY"))
                .andExpect(jsonPath("$.message").value("Unsupported order status SHIPPED"))
                .andExpect(jsonPath("$.correlationId").isString());
    }

    @Test
    void patchStatusReturns409ForInvalidTransition() throws Exception {
        UUID orderId = UUID.fromString("de305d54-75b4-431b-adb2-eb6b9e546014");
        when(adminOrderStatusService.updateStatus(orderId, "CANCELLED"))
                .thenThrow(new ConflictException("Invalid order status transition PAID -> CANCELLED"));

        mockMvc.perform(patch("/api/admin/orders/{id}/status", orderId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"CANCELLED\"}")
                .with(SecurityMockMvcRequestPostProcessors.httpBasic("admin-user", "admin-pass"))
                .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value("Invalid order status transition PAID -> CANCELLED"));
    }

    @Test
    void patchStatusReturns404ForUnknownOrder() throws Exception {
        UUID orderId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        when(adminOrderStatusService.updateStatus(orderId, "CANCELLED"))
                .thenThrow(new ResourceNotFoundException("Order not found"));

        mockMvc.perform(patch("/api/admin/orders/{id}/status", orderId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"CANCELLED\"}")
                .with(SecurityMockMvcRequestPostProcessors.httpBasic("admin-user", "admin-pass"))
                .with(SecurityMockMvcRequestPostProcessors.csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Order not found"));
    }

    private AdminOrderQueryService.OrderDetail detail(UUID orderId, String status) {
        return new AdminOrderQueryService.OrderDetail(
                orderId,
                "ET-2026-0007",
                status,
                "Ana Gómez",
                "11223344",
                "Tocar timbre",
                "ARS",
                new BigDecimal("9000.00"),
                new BigDecimal("9500.00"),
                new AdminOrderQueryService.PaymentInfo(
                        "mercadopago",
                        "pref-1",
                        "pay-1",
                        "pending",
                        Instant.parse("2026-05-01T10:00:00Z")),
                List.of(),
                Instant.parse("2026-05-01T09:00:00Z"),
                Instant.parse("2026-05-01T10:00:00Z"));
    }
}
