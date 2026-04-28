package com.eltano.ecommerce.nonregression;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.eltano.ecommerce.audit.service.AdminAuditService;
import com.eltano.ecommerce.catalog.api.PublicCatalogController;
import com.eltano.ecommerce.catalog.api.dto.PublicCatalogProductResponse;
import com.eltano.ecommerce.catalog.domain.InventoryPolicy;
import com.eltano.ecommerce.catalog.domain.ProductType;
import com.eltano.ecommerce.catalog.domain.UnitType;
import com.eltano.ecommerce.catalog.service.CatalogQueryService;
import com.eltano.ecommerce.common.api.RestExceptionHandler;
import com.eltano.ecommerce.config.SecurityConfig;
import com.eltano.ecommerce.orders.api.OrderDraftController;
import com.eltano.ecommerce.orders.service.OrderDraftService;

@WebMvcTest(controllers = { PublicCatalogController.class, OrderDraftController.class }, properties = "app.mercadopago.enabled=true")
@Import({ SecurityConfig.class, RestExceptionHandler.class })
class StorefrontSmokeMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CatalogQueryService catalogQueryService;

    @MockBean
    private OrderDraftService orderDraftService;

    @MockBean
    private AdminAuditService adminAuditService;

    @Test
    void keepsCatalogBrowsePublicAndStable() throws Exception {
        when(catalogQueryService.list(null, null)).thenReturn(List.of(new PublicCatalogProductResponse(
                UUID.fromString("11111111-1111-4111-8111-111111111111"),
                "Almendra natural premium",
                "almendra-natural-premium",
                "Ideal para snack.",
                "Frutos secos",
                "frutos-secos",
                ProductType.ENVASADO,
                InventoryPolicy.PER_VARIANT,
                null,
                List.of(new PublicCatalogProductResponse.PublicCatalogImageResponse(
                        UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"),
                        "https://cdn.example.com/a.jpg",
                        "Bolsa de almendras",
                        0,
                        true)),
                List.of(new PublicCatalogProductResponse.PublicCatalogVariantResponse(
                        UUID.fromString("22222222-2222-4222-8222-222222222222"),
                        "SKU-1",
                        UnitType.WEIGHT,
                        500,
                        "bolsa 500 g",
                        new BigDecimal("6400.00"),
                        10,
                        0,
                        null)))));

        mockMvc.perform(get("/api/catalog/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Almendra natural premium"))
                .andExpect(jsonPath("$[0].variants[0].unitLabel").value("bolsa 500 g"));
    }

    @Test
    void keepsCheckoutDraftEndpointPublicAndOperational() throws Exception {
        when(orderDraftService.createDraft(any())).thenReturn(new OrderDraftService.Result(
                UUID.fromString("93ab80b2-844e-4a35-9846-52056f8a297c"),
                "ET-2026-A1B2C3",
                "ARS",
                new BigDecimal("12000.00"),
                new BigDecimal("12000.00"),
                "Hola confirmo ET-2026-A1B2C3"));

        mockMvc.perform(post("/api/orders/drafts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "customerName": "Juan Perez",
                          "phone": "+5491112345678",
                          "items": [
                            {"variantId": "11111111-1111-1111-1111-111111111111", "quantity": 2}
                          ]
                        }
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reference").value("ET-2026-A1B2C3"));
    }

    @Test
    void keepsAdminEndpointsProtectedFromAnonymousStorefrontCalls() throws Exception {
        mockMvc.perform(get("/api/admin/products"))
                .andExpect(status().isUnauthorized());
    }
}
