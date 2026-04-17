package com.eltano.ecommerce.common.api;

public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
