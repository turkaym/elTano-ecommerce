package com.eltano.ecommerce.catalog.api;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.eltano.ecommerce.catalog.api.dto.PublicCatalogProductResponse;
import com.eltano.ecommerce.catalog.service.CatalogQueryService;

@RestController
@RequestMapping("/api/catalog/products")
public class PublicCatalogController {

    private final CatalogQueryService catalogQueryService;

    public PublicCatalogController(CatalogQueryService catalogQueryService) {
        this.catalogQueryService = catalogQueryService;
    }

    @GetMapping
    public ResponseEntity<List<PublicCatalogProductResponse>> listProducts(
            @RequestParam(name = "category", required = false) String categorySlug,
            @RequestParam(name = "text", required = false) String text) {
        return ResponseEntity.ok(catalogQueryService.list(categorySlug, text));
    }
}
