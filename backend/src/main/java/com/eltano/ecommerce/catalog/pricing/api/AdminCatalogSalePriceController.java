package com.eltano.ecommerce.catalog.pricing.api;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.UUID;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.eltano.ecommerce.catalog.pricing.service.CatalogSalePriceService;
import com.eltano.ecommerce.catalog.pricing.service.CatalogSalePriceService.ConfirmCommand;
import com.eltano.ecommerce.catalog.pricing.service.CatalogSalePriceService.ConfirmResponse;
import com.eltano.ecommerce.catalog.pricing.service.CatalogSalePriceService.PreviewResponse;

@RestController
@RequestMapping("/api/admin/catalog/sale-prices")
public class AdminCatalogSalePriceController {
    private static final MediaType XLSX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    private final CatalogSalePriceService service;

    public AdminCatalogSalePriceController(CatalogSalePriceService service) { this.service = service; }

    @GetMapping("/template")
    public ResponseEntity<ByteArrayResource> template() {
        byte[] content = service.template();
        return ResponseEntity.ok().contentType(XLSX).contentLength(content.length)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("precios-venta-catalogo.xlsx", StandardCharsets.UTF_8).build().toString())
                .body(new ByteArrayResource(content));
    }

    @PostMapping(value = "/previews", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PreviewResponse preview(@RequestParam("file") MultipartFile file, Principal principal) {
        return service.preview(file, principal.getName());
    }

    @PostMapping("/previews/{previewId}/confirm")
    public ConfirmResponse confirm(@PathVariable UUID previewId, @RequestBody ConfirmCommand command,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        return service.confirm(previewId, command, idempotencyKey);
    }
}
