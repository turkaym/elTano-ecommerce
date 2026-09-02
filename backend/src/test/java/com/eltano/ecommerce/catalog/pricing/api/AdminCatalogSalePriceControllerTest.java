package com.eltano.ecommerce.catalog.pricing.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import com.eltano.ecommerce.audit.service.AdminAuditService;
import com.eltano.ecommerce.catalog.pricing.service.CatalogSalePriceService;
import com.eltano.ecommerce.catalog.pricing.service.CatalogSalePriceService.ConfirmCommand;
import com.eltano.ecommerce.catalog.pricing.service.CatalogSalePriceService.ConfirmResponse;
import com.eltano.ecommerce.catalog.pricing.service.CatalogSalePriceService.PreviewResponse;
import com.eltano.ecommerce.catalog.pricing.service.CatalogSalePriceService.PreviewRow;
import com.eltano.ecommerce.common.api.RestExceptionHandler;
import com.eltano.ecommerce.config.SecurityConfig;

@WebMvcTest(controllers = AdminCatalogSalePriceController.class)
@Import({ SecurityConfig.class, RestExceptionHandler.class })
class AdminCatalogSalePriceControllerTest {
    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private CatalogSalePriceService service;
    @MockBean
    private AdminAuditService audit;

    @Test
    void templateIsAuthenticatedAndReturnedAsANonCacheableXlsx() throws Exception {
        when(service.template()).thenReturn(new byte[] { 1, 2, 3 });

        mockMvc.perform(get("/api/admin/catalog/sale-prices/template")
                .with(httpBasic("admin-user", "admin-pass")))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE,
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    }

    @Test
    void previewRequiresCsrfAndPassesTheAuthenticatedActor() throws Exception {
        UUID previewId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile("file", "precios.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[] { 1 });
        when(service.preview(any(), eq("admin-user"))).thenReturn(new PreviewResponse(previewId, "hash", true,
                List.of(new PreviewRow(2, "SKU", "ABC", "Almendra", "250g", null, null, List.of())), false));

        mockMvc.perform(multipart("/api/admin/catalog/sale-prices/previews").file(file)
                .with(httpBasic("admin-user", "admin-pass")))
                .andExpect(status().isForbidden());

        mockMvc.perform(multipart("/api/admin/catalog/sale-prices/previews").file(file)
                .with(httpBasic("admin-user", "admin-pass")).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.previewId").value(previewId.toString()))
                .andExpect(jsonPath("$.valid").value(true));
        verify(service).preview(any(), eq("admin-user"));
    }

    @Test
    void confirmationRequiresCsrfAndIdempotencyKey() throws Exception {
        UUID previewId = UUID.randomUUID();
        when(service.confirm(eq(previewId), any(ConfirmCommand.class), eq("confirm-1")))
                .thenReturn(new ConfirmResponse(previewId, false, Instant.parse("2026-09-02T12:00:00Z")));

        mockMvc.perform(post("/api/admin/catalog/sale-prices/previews/{id}/confirm", previewId)
                .with(httpBasic("admin-user", "admin-pass")).with(csrf())
                .contentType("application/json").content("{\"previewHash\":\"hash\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/admin/catalog/sale-prices/previews/{id}/confirm", previewId)
                .with(httpBasic("admin-user", "admin-pass")).with(csrf())
                .header("Idempotency-Key", "confirm-1")
                .contentType("application/json").content("{\"previewHash\":\"hash\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reused").value(false));
    }
}
