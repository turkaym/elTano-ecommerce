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

import com.eltano.ecommerce.catalog.api.dto.AdminCategoryResponse;
import com.eltano.ecommerce.catalog.api.dto.AdminCategoryUpsertRequest;
import com.eltano.ecommerce.catalog.service.AdminCategoryService;

import jakarta.validation.Valid;

@Validated
@RestController
@RequestMapping("/api/admin/categories")
public class AdminCategoryController {

    private final AdminCategoryService adminCategoryService;

    public AdminCategoryController(AdminCategoryService adminCategoryService) {
        this.adminCategoryService = adminCategoryService;
    }

    @PostMapping
    public ResponseEntity<AdminCategoryResponse> create(@Valid @RequestBody AdminCategoryUpsertRequest request) {
        AdminCategoryResponse response = adminCategoryService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<AdminCategoryResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody AdminCategoryUpsertRequest request) {
        return ResponseEntity.ok(adminCategoryService.update(id, request));
    }

    @GetMapping
    public ResponseEntity<List<AdminCategoryResponse>> list() {
        return ResponseEntity.ok(adminCategoryService.list());
    }
}
