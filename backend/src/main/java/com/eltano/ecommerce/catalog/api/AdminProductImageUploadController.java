package com.eltano.ecommerce.catalog.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.eltano.ecommerce.catalog.api.dto.AdminProductImageUploadResponse;
import com.eltano.ecommerce.catalog.service.AdminProductImageUploadService;

@Validated
@RestController
@RequestMapping("/api/admin/uploads/product-images")
public class AdminProductImageUploadController {

    private final AdminProductImageUploadService uploadService;

    public AdminProductImageUploadController(AdminProductImageUploadService uploadService) {
        this.uploadService = uploadService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AdminProductImageUploadResponse> upload(@RequestParam("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED).body(uploadService.store(file));
    }
}
