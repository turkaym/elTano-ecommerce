package com.eltano.ecommerce.catalog.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.eltano.ecommerce.catalog.api.dto.AdminProductImageUploadResponse;
import com.eltano.ecommerce.catalog.config.ProductImageUploadProperties;

@Service
public class AdminProductImageUploadService {

    private static final String INVALID_TYPE_MESSAGE = "Product image must be a JPG, PNG, or WebP file";
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png", "image/webp");
    private static final Map<String, String> EXTENSIONS_BY_CONTENT_TYPE = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "image/webp", ".webp");

    private final ProductImageUploadProperties properties;

    public AdminProductImageUploadService(ProductImageUploadProperties properties) {
        this.properties = properties;
    }

    public AdminProductImageUploadResponse store(MultipartFile file) {
        validate(file);
        String contentType = normalizeContentType(file.getContentType());
        String filename = UUID.randomUUID() + "-" + sanitizeFilename(file.getOriginalFilename(), contentType);
        Path target = properties.getDirectory().toAbsolutePath().normalize().resolve(filename);

        try {
            Files.createDirectories(target.getParent());
            file.transferTo(target);
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not store product image", ex);
        }

        return new AdminProductImageUploadResponse(
                properties.normalizedPublicPath() + "/" + filename,
                contentType,
                file.getSize());
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Product image file is required");
        }

        String contentType = normalizeContentType(file.getContentType());
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new IllegalArgumentException(INVALID_TYPE_MESSAGE);
        }

        if (file.getSize() > properties.getMaxSizeBytes()) {
            throw new IllegalArgumentException("Product image must be " + properties.getMaxSizeBytes() + " bytes or smaller");
        }
    }

    private String sanitizeFilename(String originalFilename, String contentType) {
        String fallback = "product-image" + EXTENSIONS_BY_CONTENT_TYPE.get(contentType);
        if (originalFilename == null || originalFilename.isBlank()) {
            return fallback;
        }

        String normalized = Normalizer.normalize(Path.of(originalFilename).getFileName().toString(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9.]+", "-")
                .replaceAll("^-+|-+$", "");
        if (normalized.isBlank()) {
            return fallback;
        }
        return normalized.contains(".") ? normalized : normalized + EXTENSIONS_BY_CONTENT_TYPE.get(contentType);
    }

    private String normalizeContentType(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
    }
}
