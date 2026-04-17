package com.eltano.ecommerce.catalog.api;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.eltano.ecommerce.catalog.api.dto.AdminProductResponse;
import com.eltano.ecommerce.catalog.api.dto.AdminProductUpsertRequest;
import com.eltano.ecommerce.catalog.service.AdminProductService;

import jakarta.validation.Valid;

@Validated
@RestController
@RequestMapping("/api/admin/products")
public class AdminProductController {

    private final AdminProductService adminProductService;

    public AdminProductController(AdminProductService adminProductService) {
        this.adminProductService = adminProductService;
    }

    @PostMapping
    public ResponseEntity<AdminProductResponse> create(@Valid @RequestBody AdminProductUpsertRequest request) {
        AdminProductResponse response = adminProductService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<AdminProductResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody AdminProductUpsertRequest request) {
        return ResponseEntity.ok(adminProductService.update(id, request));
    }

    @GetMapping
    public ResponseEntity<List<AdminProductResponse>> list() {
        return ResponseEntity.ok(adminProductService.list());
    }
}
