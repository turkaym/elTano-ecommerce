package com.eltano.ecommerce.catalog.jobs.api;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.eltano.ecommerce.catalog.jobs.AdminCatalogJobService;
import com.eltano.ecommerce.catalog.jobs.api.dto.AdminCatalogJobResponse;
import com.eltano.ecommerce.catalog.jobs.api.dto.AdminCatalogJobRowResponse;
import com.eltano.ecommerce.catalog.jobs.domain.AdminCatalogJob;
import com.eltano.ecommerce.catalog.jobs.domain.AdminCatalogJobRow;

@Validated
@RestController
@RequestMapping("/api/admin/catalog/jobs")
public class AdminCatalogJobController {

    private final AdminCatalogJobService adminCatalogJobService;

    public AdminCatalogJobController(AdminCatalogJobService adminCatalogJobService) {
        this.adminCatalogJobService = adminCatalogJobService;
    }

    @PostMapping(value = "/import", consumes = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<AdminCatalogJobResponse> importCsv(
            @RequestParam(name = "format", defaultValue = "csv") String format,
            @RequestBody String payload,
            Authentication authentication) {
        ensureCsvSupported(format, "Import");
        AdminCatalogJob job = adminCatalogJobService.enqueueCsvImport(actor(authentication), payload);
        return acceptedJob(job);
    }

    @PostMapping("/export")
    public ResponseEntity<AdminCatalogJobResponse> exportCsv(
            @RequestParam(name = "format", defaultValue = "csv") String format,
            Authentication authentication) {
        ensureCsvSupported(format, "Export");
        AdminCatalogJob job = adminCatalogJobService.enqueueCsvExport(actor(authentication));
        return acceptedJob(job);
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<Void> cancelJob(@PathVariable UUID id) {
        adminCatalogJobService.cancelJob(id);
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<AdminCatalogJobResponse> getJob(@PathVariable UUID id) {
        return ResponseEntity.ok(toJobResponse(adminCatalogJobService.getJob(id)));
    }

    @GetMapping("/{id}/rows")
    public ResponseEntity<List<AdminCatalogJobRowResponse>> getRows(@PathVariable UUID id) {
        List<AdminCatalogJobRowResponse> rows = adminCatalogJobService.listRows(id).stream()
                .map(this::toRowResponse)
                .toList();
        return ResponseEntity.ok(rows);
    }

    @GetMapping(value = "/{id}/report", produces = "text/csv")
    public ResponseEntity<byte[]> getValidationReport(@PathVariable UUID id) {
        byte[] body = adminCatalogJobService.validationReportCsv(id).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=validation-report-" + id + ".csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(body);
    }

    @GetMapping(value = "/{id}/export-file", produces = "text/csv")
    public ResponseEntity<byte[]> getExportFile(@PathVariable UUID id) {
        byte[] body = adminCatalogJobService.exportCsv(id).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=catalog-export-" + id + ".csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(body);
    }

    private void ensureCsvSupported(String format, String operation) {
        if (!"csv".equalsIgnoreCase(format)) {
            throw new IllegalArgumentException(operation + " requires format=csv");
        }
    }

    private String actor(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            return "system";
        }
        return authentication.getName();
    }

    private AdminCatalogJobResponse toJobResponse(AdminCatalogJob job) {
        return new AdminCatalogJobResponse(
                job.getId(),
                job.getJobType().name(),
                job.getStatus().name(),
                job.getCreatedBy(),
                job.getSourceFormat().name(),
                job.getSummary(),
                job.getCreatedAt(),
                job.getUpdatedAt(),
                job.getCompletedAt());
    }

    private ResponseEntity<AdminCatalogJobResponse> acceptedJob(AdminCatalogJob job) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(toJobResponse(job));
    }

    private AdminCatalogJobRowResponse toRowResponse(AdminCatalogJobRow row) {
        return new AdminCatalogJobRowResponse(
                row.getId(),
                row.getRowNumber(),
                row.getOutcome().name(),
                row.getErrorCode(),
                row.getErrorMessage(),
                row.getPayloadJson(),
                row.getCreatedAt());
    }
}
