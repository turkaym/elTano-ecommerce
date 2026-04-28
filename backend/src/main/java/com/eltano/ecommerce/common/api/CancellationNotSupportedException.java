package com.eltano.ecommerce.common.api;

public class CancellationNotSupportedException extends RuntimeException {

    public static final String CODE = "CANCELLATION_NOT_SUPPORTED";

    public CancellationNotSupportedException(String message) {
        super(message);
    }
}
