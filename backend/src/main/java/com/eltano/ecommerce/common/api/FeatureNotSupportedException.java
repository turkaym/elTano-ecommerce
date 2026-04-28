package com.eltano.ecommerce.common.api;

public class FeatureNotSupportedException extends RuntimeException {

    public FeatureNotSupportedException(String message) {
        super(message);
    }
}
