package com.eltano.ecommerce.inventory.api;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.eltano.ecommerce.inventory.service.InventoryExportService;

@RestController
@RequestMapping("/api/admin/inventory")
public class AdminInventoryExportController {
    private static final MediaType XLSX = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    private static final DateTimeFormatter FILENAME_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private final InventoryExportService service;

    public AdminInventoryExportController(InventoryExportService service) { this.service = service; }

    @GetMapping("/export.xlsx")
    public ResponseEntity<ByteArrayResource> export() {
        byte[] content = service.export();
        String filename = "inventario-completo-" + LocalDateTime.now().format(FILENAME_TIME) + ".xlsx";
        return ResponseEntity.ok().contentType(XLSX).contentLength(content.length)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(filename, StandardCharsets.UTF_8).build().toString())
                .body(new ByteArrayResource(content));
    }
}
