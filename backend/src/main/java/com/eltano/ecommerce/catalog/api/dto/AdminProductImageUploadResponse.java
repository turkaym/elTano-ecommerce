package com.eltano.ecommerce.catalog.api.dto;

public record AdminProductImageUploadResponse(
        String url,
        String contentType,
        long sizeBytes) {
}
