package com.eltano.ecommerce.catalog.config;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.uploads.product-images")
public class ProductImageUploadProperties {

    private Path directory = Path.of("uploads", "product-images");
    private String publicPath = "/uploads/product-images";
    private long maxSizeBytes = 5 * 1024 * 1024;

    public Path getDirectory() {
        return directory;
    }

    public void setDirectory(Path directory) {
        this.directory = directory;
    }

    public String getPublicPath() {
        return publicPath;
    }

    public void setPublicPath(String publicPath) {
        this.publicPath = publicPath;
    }

    public long getMaxSizeBytes() {
        return maxSizeBytes;
    }

    public void setMaxSizeBytes(long maxSizeBytes) {
        this.maxSizeBytes = maxSizeBytes;
    }

    public String normalizedPublicPath() {
        String value = publicPath == null || publicPath.isBlank() ? "/uploads/product-images" : publicPath.trim();
        if (!value.startsWith("/")) {
            value = "/" + value;
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
