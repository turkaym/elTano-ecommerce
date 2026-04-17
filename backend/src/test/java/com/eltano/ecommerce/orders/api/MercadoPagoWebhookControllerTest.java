package com.eltano.ecommerce.orders.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.eltano.ecommerce.common.api.RestExceptionHandler;
import com.eltano.ecommerce.config.SecurityConfig;
import com.eltano.ecommerce.orders.service.payment.MercadoPagoWebhookService;

@WebMvcTest(controllers = MercadoPagoWebhookController.class)
@Import({ SecurityConfig.class, RestExceptionHandler.class })
class MercadoPagoWebhookControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private MercadoPagoWebhookService webhookService;

    @Test
    void returns403WhenSignatureHeaderMissing() throws Exception {
        when(webhookService.process(any())).thenThrow(new MercadoPagoWebhookService.InvalidWebhookSignatureException());

        mockMvc.perform(post("/api/payments/mercadopago/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "id": "evt-1",
                          "type": "payment",
                          "data": { "id": "pay-1" }
                        }
                        """))
                .andExpect(status().isForbidden());
    }

    @Test
    void acknowledgesDuplicateEventAsNoOp() throws Exception {
        when(webhookService.process(any())).thenReturn(
                new MercadoPagoWebhookService.Result(true, "DUPLICATE_IGNORED"));

        mockMvc.perform(post("/api/payments/mercadopago/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .header("x-signature", "sig-valid")
                .header("x-request-id", "req-1")
                .content("""
                        {
                          "id": "evt-1",
                          "topic": "payment",
                          "data": { "id": "pay-123" }
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.received").value(true))
                .andExpect(jsonPath("$.outcome").value("DUPLICATE_IGNORED"));

        ArgumentCaptor<MercadoPagoWebhookService.Command> commandCaptor = ArgumentCaptor
                .forClass(MercadoPagoWebhookService.Command.class);
        verify(webhookService).process(commandCaptor.capture());
        MercadoPagoWebhookService.Command command = commandCaptor.getValue();
        org.junit.jupiter.api.Assertions.assertEquals("evt-1", command.providerEventId());
        org.junit.jupiter.api.Assertions.assertEquals("pay-123", command.paymentExternalId());
    }

    @Test
    void ignoresRegressiveEventAfterPaidState() throws Exception {
        when(webhookService.process(any())).thenReturn(
                new MercadoPagoWebhookService.Result(true, "REGRESSION_IGNORED"));

        mockMvc.perform(post("/api/payments/mercadopago/webhook")
                .contentType(MediaType.APPLICATION_JSON)
                .header("x-signature", "sig-valid")
                .header("x-request-id", "req-2")
                .content("""
                        {
                          "id": "evt-2",
                          "topic": "payment"
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("REGRESSION_IGNORED"));
    }

    @Test
    void fallsBackToQueryParamsWhenBodyMissingPaymentFields() throws Exception {
        when(webhookService.process(any())).thenReturn(
                new MercadoPagoWebhookService.Result(true, "IDEMPOTENT_SAME_STATE"));

        mockMvc.perform(post("/api/payments/mercadopago/webhook")
                .queryParam("type", "payment")
                .queryParam("id", "pay-query-1")
                .contentType(MediaType.APPLICATION_JSON)
                .header("x-signature", "sig-valid")
                .header("x-request-id", "req-query")
                .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("IDEMPOTENT_SAME_STATE"));

        ArgumentCaptor<MercadoPagoWebhookService.Command> commandCaptor = ArgumentCaptor
                .forClass(MercadoPagoWebhookService.Command.class);
        verify(webhookService).process(commandCaptor.capture());
        MercadoPagoWebhookService.Command command = commandCaptor.getValue();
        org.junit.jupiter.api.Assertions.assertEquals("pay-query-1", command.providerEventId());
        org.junit.jupiter.api.Assertions.assertEquals("pay-query-1", command.paymentExternalId());
    }

    @Test
    void acceptsActionVariantWithQueryDataIdFallback() throws Exception {
        when(webhookService.process(any())).thenReturn(
                new MercadoPagoWebhookService.Result(true, "PAID_APPLIED"));

        mockMvc.perform(post("/api/payments/mercadopago/webhook")
                .queryParam("data.id", "pay-q-data")
                .queryParam("topic", "payment")
                .contentType(MediaType.APPLICATION_JSON)
                .header("x-signature", "sig-valid")
                .header("x-request-id", "req-action")
                .content("""
                        {
                          "id": "evt-action-1",
                          "action": "payment.updated"
                        }
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("PAID_APPLIED"));

        ArgumentCaptor<MercadoPagoWebhookService.Command> commandCaptor = ArgumentCaptor
                .forClass(MercadoPagoWebhookService.Command.class);
        verify(webhookService).process(commandCaptor.capture());
        MercadoPagoWebhookService.Command command = commandCaptor.getValue();
        org.junit.jupiter.api.Assertions.assertEquals("evt-action-1", command.providerEventId());
        org.junit.jupiter.api.Assertions.assertEquals("pay-q-data", command.paymentExternalId());
    }
}
