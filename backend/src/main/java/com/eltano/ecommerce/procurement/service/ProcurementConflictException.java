package com.eltano.ecommerce.procurement.service;

public class ProcurementConflictException extends RuntimeException {
    private final String code;
    public ProcurementConflictException(String code, String message) { super(message); this.code = code; }
    public String getCode() { return code; }
}
