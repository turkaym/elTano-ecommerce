package com.eltano.ecommerce.common.api;

import java.util.List;

public class UnprocessableEntityException extends RuntimeException {

    private final List<FieldError> fieldErrors;

    public UnprocessableEntityException(String message) {
        this(message, List.of());
    }

    public UnprocessableEntityException(String message, List<FieldError> fieldErrors) {
        super(message);
        this.fieldErrors = fieldErrors == null ? List.of() : List.copyOf(fieldErrors);
    }

    public List<FieldError> getFieldErrors() {
        return fieldErrors;
    }

    public record FieldError(String field, String message) {
    }
}
