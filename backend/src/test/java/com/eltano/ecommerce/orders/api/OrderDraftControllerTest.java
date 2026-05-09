package com.eltano.ecommerce.orders.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.eltano.ecommerce.common.api.ConflictException;
import com.eltano.ecommerce.common.api.MercadoPagoRequestException;
import com.eltano.ecommerce.common.api.RestExceptionHandler;
import com.eltano.ecommerce.config.SecurityConfig;
import com.eltano.ecommerce.audit.service.AdminAuditService;
import com.eltano.ecommerce.orders.service.OrderDraftService;

@WebMvcTest(controllers = OrderDraftController.class, properties = "app.mercadopago.enabled=true")
@Import({ SecurityConfig.class, RestExceptionHandler.class })
class OrderDraftControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderDraftService orderDraftService;

    @MockBean
    private AdminAuditService adminAuditService;

    @Test
    void createDraftReturns201AndBody() throws Exception {
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
                          "note": "Tocar timbre",
                          "items": [
                            {"variantId": "11111111-1111-1111-1111-111111111111", "quantity": 2}
                          ]
                        }
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reference").value("ET-2026-A1B2C3"))
                .andExpect(jsonPath("$.currency").value("ARS"))
                .andExpect(jsonPath("$.whatsappMessage").value("Hola confirmo ET-2026-A1B2C3"));
    }

    @Test
    void createDraftReturns400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/api/orders/drafts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "customerName": "",
                          "phone": "",
                          "items": []
                        }
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.fieldErrors").isArray());
    }

    @Test
    void createDraftReturns409ForStockConflict() throws Exception {
        when(orderDraftService.createDraft(any())).thenThrow(new ConflictException("Insufficient stock"));

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
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Insufficient stock"));
    }

    @Test
    void createDraftReturns409ForBulkWeightBoundaryConflict() throws Exception {
        when(orderDraftService.createDraft(any())).thenThrow(new ConflictException("Insufficient stock: requires 1000g"));

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
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Insufficient stock: requires 1000g"));
    }

    @Test
    void createDraftReturns400WhenServiceRejectsPayload() throws Exception {
        when(orderDraftService.createDraft(any())).thenThrow(new IllegalArgumentException("Variant not found"));

        mockMvc.perform(post("/api/orders/drafts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "customerName": "Juan Perez",
                          "phone": "+5491112345678",
                          "items": [
                            {"variantId": "11111111-1111-1111-1111-111111111111", "quantity": 1}
                          ]
                        }
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Variant not found"));
    }

    @Test
    void createDraftReturns400WhenVariantSelectionIsMissing() throws Exception {
        when(orderDraftService.createDraft(any())).thenThrow(new IllegalArgumentException("Variant selection required"));

        mockMvc.perform(post("/api/orders/drafts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "customerName": "Juan Perez",
                          "phone": "+5491112345678",
                          "items": [
                            {"variantId": "11111111-1111-1111-1111-111111111111", "quantity": 1}
                          ]
                        }
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Variant selection required"));
    }

    @Test
    void createDraftReturns400WhenVariantPolicyIsIncompatible() throws Exception {
        when(orderDraftService.createDraft(any())).thenThrow(new IllegalArgumentException("Variant incompatible with product policy"));

        mockMvc.perform(post("/api/orders/drafts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "customerName": "Juan Perez",
                          "phone": "+5491112345678",
                          "items": [
                            {"variantId": "11111111-1111-1111-1111-111111111111", "quantity": 1}
                          ]
                        }
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Variant incompatible with product policy"));
    }

    @Test
    void adminEndpointsRemainProtectedInMvpScope() throws Exception {
        mockMvc.perform(post("/api/admin/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                // Contract alignment only: current security chain rejects unauthenticated
                // admin writes at CSRF/auth filter precedence with 403 (not 401).
                .andExpect(status().isForbidden());
    }

    @Test
    void startPaymentPreferenceIsPublicAndReturnsInitPointAndPreferenceId() throws Exception {
        when(orderDraftService.startPaymentPreference(any())).thenReturn(
                new OrderDraftService.PaymentPreferenceResult(
                        UUID.fromString("93ab80b2-844e-4a35-9846-52056f8a297c"),
                        "pref-123",
                        "https://www.mercadopago.com.ar/checkout/v1/redirect?pref_id=pref-123"));

        mockMvc.perform(post("/api/orders/drafts/93ab80b2-844e-4a35-9846-52056f8a297c/payment-preference"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.draftId").value("93ab80b2-844e-4a35-9846-52056f8a297c"))
                .andExpect(jsonPath("$.preferenceId").value("pref-123"))
                .andExpect(jsonPath("$.initPoint").value(
                        "https://www.mercadopago.com.ar/checkout/v1/redirect?pref_id=pref-123"));

        verify(orderDraftService).startPaymentPreference(any());
    }

    @Test
    void paymentPreferencePreflightIsAllowedForFrontendOrigin() throws Exception {
        mockMvc.perform(options("/api/orders/drafts/93ab80b2-844e-4a35-9846-52056f8a297c/payment-preference")
                .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"));
    }

    @Test
    void getDraftPaymentStatusReturnsCanonicalStatus() throws Exception {
        when(orderDraftService.getPaymentStatus(UUID.fromString("93ab80b2-844e-4a35-9846-52056f8a297c"))).thenReturn(
                new OrderDraftService.PaymentStatusResult(
                        UUID.fromString("93ab80b2-844e-4a35-9846-52056f8a297c"),
                        "ET-2026-A1B2C3",
                        "PAYMENT_PENDING",
                        "2026-04-15T00:00:00Z",
                        true));

        mockMvc.perform(get("/api/orders/drafts/93ab80b2-844e-4a35-9846-52056f8a297c/payment-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reference").value("ET-2026-A1B2C3"))
                .andExpect(jsonPath("$.status").value("PAYMENT_PENDING"))
                .andExpect(jsonPath("$.canRetry").value(true));
    }

    @Test
    void startPaymentPreferenceReturns400InsteadOf401WhenFeatureDisabled() throws Exception {
        when(orderDraftService.startPaymentPreference(any()))
                .thenThrow(new IllegalStateException("Mercado Pago checkout is disabled"));

        mockMvc.perform(post("/api/orders/drafts/93ab80b2-844e-4a35-9846-52056f8a297c/payment-preference"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Mercado Pago checkout is disabled"));
    }

    @Test
    void startPaymentPreferenceReturns400ForCheckoutUrlMisconfiguration() throws Exception {
        when(orderDraftService.startPaymentPreference(any()))
                .thenThrow(new IllegalStateException(
                        "Invalid MP_CHECKOUT_SUCCESS_URL: value must be a non-blank absolute http/https URL"));

        mockMvc.perform(post("/api/orders/drafts/93ab80b2-844e-4a35-9846-52056f8a297c/payment-preference"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("MP_CHECKOUT_SUCCESS_URL")));
    }

    @Test
    void startPaymentPreferenceMapsMercadoPago4xxTo400() throws Exception {
        when(orderDraftService.startPaymentPreference(any()))
                .thenThrow(new MercadoPagoRequestException(
                        HttpStatus.BAD_REQUEST,
                        "{\"message\":\"invalid items[0].unit_price\"}",
                        null));

        mockMvc.perform(post("/api/orders/drafts/93ab80b2-844e-4a35-9846-52056f8a297c/payment-preference"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Mercado Pago request failed")))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("invalid items[0].unit_price")));
    }

    @Test
    void startPaymentPreferenceMapsMercadoPago5xxTo502() throws Exception {
        when(orderDraftService.startPaymentPreference(any()))
                .thenThrow(new MercadoPagoRequestException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "{\"message\":\"internal_error\"}",
                        null));

        mockMvc.perform(post("/api/orders/drafts/93ab80b2-844e-4a35-9846-52056f8a297c/payment-preference"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Mercado Pago request failed")));
    }

    @Test
    void startPaymentPreferenceIncludesUpstreamStatusWhenMercadoPagoBodyIsEmpty() throws Exception {
        when(orderDraftService.startPaymentPreference(any()))
                .thenThrow(new MercadoPagoRequestException(
                        HttpStatus.BAD_REQUEST,
                        "   ",
                        null));

        mockMvc.perform(post("/api/orders/drafts/93ab80b2-844e-4a35-9846-52056f8a297c/payment-preference"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Mercado Pago request failed (upstream 400)"));
    }
}
