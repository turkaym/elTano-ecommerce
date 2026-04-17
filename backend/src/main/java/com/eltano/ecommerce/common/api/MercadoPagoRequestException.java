package com.eltano.ecommerce.common.api;

import org.springframework.http.HttpStatusCode;

public class MercadoPagoRequestException extends RuntimeException {

    private final int upstreamStatusCode;
    private final String upstreamResponseBody;

    public MercadoPagoRequestException(HttpStatusCode upstreamStatusCode, String upstreamResponseBody, Throwable cause) {
        super("Mercado Pago request failed", cause);
        this.upstreamStatusCode = upstreamStatusCode.value();
        this.upstreamResponseBody = upstreamResponseBody;
    }

    public int getUpstreamStatusCode() {
        return upstreamStatusCode;
    }

    public String getUpstreamResponseBody() {
        return upstreamResponseBody;
    }
}
