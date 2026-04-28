package com.eltano.ecommerce.orders;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import com.eltano.ecommerce.common.api.ResourceNotFoundException;
import com.eltano.ecommerce.common.api.RestExceptionHandler;
import com.eltano.ecommerce.config.SecurityConfig;
import com.eltano.ecommerce.orders.api.AdminOrderController;
import com.eltano.ecommerce.audit.service.AdminAuditService;
import com.eltano.ecommerce.orders.service.AdminOrderQueryService;

@WebMvcTest(controllers = AdminOrderController.class)
@Import({ SecurityConfig.class, RestExceptionHandler.class })
class AdminOrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminOrderQueryService adminOrderQueryService;

    @MockBean
    private AdminAuditService adminAuditService;

    @Test
    void listSupportsFiltersAndPagingMetadata() throws Exception {
        AdminOrderQueryService.OrderListItem item = new AdminOrderQueryService.OrderListItem(
                UUID.fromString("de305d54-75b4-431b-adb2-eb6b9e546014"),
                "ET-2026-ABC123",
                "DRAFT",
                "Juan Perez",
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
                eq(PageRequest.of(1, 10, Sort.by(Sort.Direction.DESC, "createdAt")))))
                .thenReturn(result);

        mockMvc.perform(get("/api/admin/orders")
                .param("status", "DRAFT")
                .param("from", "2026-04-01")
                .param("to", "2026-04-30")
                .param("customer", "juan")
                .param("reference", "ET-2026")
                .param("page", "1")
                .param("size", "10")
                .with(SecurityMockMvcRequestPostProcessors.httpBasic("admin-user", "admin-pass")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].reference").value("ET-2026-ABC123"))
                .andExpect(jsonPath("$.items[0].status").value("DRAFT"))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
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
}
