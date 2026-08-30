package com.eltano.ecommerce.procurement.draft.service;

import org.springframework.http.HttpStatus;

public class PurchaseDraftException extends RuntimeException {
    private final String code;
    private final HttpStatus status;

    public PurchaseDraftException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public String getCode() { return code; }
    public HttpStatus getStatus() { return status; }
}
