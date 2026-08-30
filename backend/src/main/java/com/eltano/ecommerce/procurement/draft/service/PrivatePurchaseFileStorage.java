package com.eltano.ecommerce.procurement.draft.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;

@Service
public class PrivatePurchaseFileStorage {
    private final Path directory;

    public PrivatePurchaseFileStorage(@Value("${app.procurement.purchase-files.directory:uploads/private/purchases}") Path directory) {
        this.directory = directory.toAbsolutePath().normalize();
    }

    public String store(byte[] content) {
        String key = UUID.randomUUID() + ".xlsx";
        Path target = resolve(key);
        try {
            Files.createDirectories(directory);
            Files.write(target, content, StandardOpenOption.CREATE_NEW);
            return key;
        } catch (IOException exception) {
            throw new UncheckedIOException("No se pudo guardar el archivo de compra.", exception);
        }
    }

    public Resource load(String key) {
        try {
            Path source = resolve(key);
            if (!Files.isRegularFile(source)) throw new PurchaseDraftException(org.springframework.http.HttpStatus.NOT_FOUND, "SOURCE_FILE_NOT_FOUND", "No se encontro el archivo original.");
            return new UrlResource(source.toUri());
        } catch (java.net.MalformedURLException exception) {
            throw new IllegalStateException("No se pudo abrir el archivo de compra.", exception);
        }
    }

    public void deleteQuietly(String key) {
        if (key == null) return;
        try { Files.deleteIfExists(resolve(key)); }
        catch (IOException ignored) { }
    }

    private Path resolve(String key) {
        Path resolved = directory.resolve(key).normalize();
        if (!resolved.startsWith(directory)) throw new IllegalArgumentException("Clave de archivo invalida.");
        return resolved;
    }
}
