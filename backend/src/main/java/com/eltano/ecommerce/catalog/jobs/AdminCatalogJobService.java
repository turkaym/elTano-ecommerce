package com.eltano.ecommerce.catalog.jobs;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.eltano.ecommerce.catalog.domain.Category;
import com.eltano.ecommerce.catalog.domain.Product;
import com.eltano.ecommerce.catalog.jobs.domain.AdminCatalogJob;
import com.eltano.ecommerce.catalog.jobs.domain.AdminCatalogJobRow;
import com.eltano.ecommerce.catalog.jobs.domain.AdminCatalogJobRowOutcome;
import com.eltano.ecommerce.catalog.jobs.domain.AdminCatalogJobStatus;
import com.eltano.ecommerce.catalog.jobs.domain.AdminCatalogJobType;
import com.eltano.ecommerce.catalog.jobs.domain.AdminCatalogSourceFormat;
import com.eltano.ecommerce.catalog.jobs.repository.AdminCatalogJobRepository;
import com.eltano.ecommerce.catalog.jobs.repository.AdminCatalogJobRowRepository;
import com.eltano.ecommerce.catalog.jobs.worker.AdminCatalogJobRetryPolicy;
import com.eltano.ecommerce.catalog.repository.CategoryRepository;
import com.eltano.ecommerce.catalog.repository.ProductRepository;
import com.eltano.ecommerce.common.api.CancellationNotSupportedException;
import com.eltano.ecommerce.common.api.ResourceNotFoundException;
import com.eltano.ecommerce.common.api.UnprocessableEntityException;

@Service
public class AdminCatalogJobService {

    private static final String IMPORT_HEADER = "categorySlug,name,slug,description";
    private static final String VALIDATION_REPORT_HEADER = "rowNumber,errorCode,errorMessage,payload";

    private final AdminCatalogJobRepository jobRepository;
    private final AdminCatalogJobRowRepository jobRowRepository;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    public AdminCatalogJobService(
            AdminCatalogJobRepository jobRepository,
            AdminCatalogJobRowRepository jobRowRepository,
            CategoryRepository categoryRepository,
            ProductRepository productRepository) {
        this.jobRepository = jobRepository;
        this.jobRowRepository = jobRowRepository;
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
    }

    @Transactional
    public AdminCatalogJob startCsvImport(String createdBy, String csvPayload) {
        return enqueueCsvImport(createdBy, csvPayload);
    }

    @Transactional
    public AdminCatalogJob enqueueCsvImport(String createdBy, String csvPayload) {
        validateImportPayload(csvPayload);
        AdminCatalogJob job = newJob(createdBy, AdminCatalogJobType.IMPORT, AdminCatalogSourceFormat.CSV);
        return jobRepository.save(job);
    }

    @Transactional
    public AdminCatalogJob startCsvExport(String createdBy) {
        return enqueueCsvExport(createdBy);
    }

    @Transactional
    public AdminCatalogJob enqueueCsvExport(String createdBy) {
        AdminCatalogJob job = newJob(createdBy, AdminCatalogJobType.EXPORT, AdminCatalogSourceFormat.CSV);
        return jobRepository.save(job);
    }

    @Transactional
    public void cancelJob(UUID jobId) {
        ensureJobExists(jobId);
        throw new CancellationNotSupportedException("Cancellation is not supported for admin catalog jobs in this phase.");
    }

    @Transactional(readOnly = true)
    public AdminCatalogJob getJob(UUID id) {
        return jobRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Catalog job not found"));
    }

    @Transactional(readOnly = true)
    public List<AdminCatalogJobRow> listRows(UUID jobId) {
        ensureJobExists(jobId);
        return jobRowRepository.findByJobIdOrderByRowNumberAsc(jobId);
    }

    @Transactional(readOnly = true)
    public String validationReportCsv(UUID jobId) {
        List<AdminCatalogJobRow> rows = listRows(jobId);
        String body = rows.stream()
                .filter(row -> row.getOutcome() == AdminCatalogJobRowOutcome.FAILED)
                .map(row -> row.getRowNumber() + ","
                        + escapeCsv(row.getErrorCode()) + ","
                        + escapeCsv(row.getErrorMessage()) + ","
                        + escapeCsv(row.getPayloadJson()))
                .collect(Collectors.joining("\n"));
        return body.isBlank() ? VALIDATION_REPORT_HEADER : VALIDATION_REPORT_HEADER + "\n" + body;
    }

    @Transactional(readOnly = true)
    public String exportCsv(UUID jobId) {
        AdminCatalogJob job = getJob(jobId);
        if (job.getJobType() != AdminCatalogJobType.EXPORT) {
            throw new ResourceNotFoundException("Export artifact not found for the job");
        }

        List<Product> products = productRepository.findAllWithRelations();
        String rows = products.stream()
                .map(product -> product.getCategory().getSlug() + ","
                        + escapeCsv(product.getName()) + ","
                        + escapeCsv(product.getSlug()) + ","
                        + escapeCsv(product.getDescription()))
                .collect(Collectors.joining("\n"));
        if (rows.isBlank()) {
            return IMPORT_HEADER;
        }
        return IMPORT_HEADER + "\n" + rows;
    }

    @Transactional
    public String executeClaimedJob(UUID jobId) {
        AdminCatalogJob job = getJob(jobId);
        if (job.getJobType() == AdminCatalogJobType.EXPORT) {
            long exportedRows = productRepository.count();
            return "processed=" + exportedRows + ",succeeded=" + exportedRows + ",failed=0";
        }

        throw new AdminCatalogJobRetryPolicy.RetryableJobException(
                "Import execution is deferred until payload persistence is added");
    }

    private CsvRowResult processImportRow(AdminCatalogJob job, int rowNumber, String line) {
        String payload = line.trim();
        String[] tokens = payload.split(",", -1);
        if (tokens.length < 4) {
            return saveRow(job, rowNumber, AdminCatalogJobRowOutcome.FAILED, "INVALID_ROW", "Expected 4 columns", payload);
        }

        String categorySlug = tokens[0].trim();
        String name = tokens[1].trim();
        String slug = tokens[2].trim();
        String description = tokens[3].trim();

        if (categorySlug.isBlank()) {
            return saveRow(job, rowNumber, AdminCatalogJobRowOutcome.FAILED, "CATEGORY_REQUIRED", "Category slug is required", payload);
        }
        if (name.isBlank()) {
            return saveRow(job, rowNumber, AdminCatalogJobRowOutcome.FAILED, "NAME_REQUIRED", "Product name is required", payload);
        }
        if (slug.isBlank()) {
            return saveRow(job, rowNumber, AdminCatalogJobRowOutcome.FAILED, "SLUG_REQUIRED", "Product slug is required", payload);
        }

        Category category = categoryRepository.findBySlugIgnoreCase(categorySlug)
                .orElse(null);
        if (category == null) {
            return saveRow(
                    job,
                    rowNumber,
                    AdminCatalogJobRowOutcome.FAILED,
                    "CATEGORY_NOT_FOUND",
                    "Category slug not found: " + categorySlug,
                    payload);
        }

        if (productRepository.existsBySlugIgnoreCase(slug)) {
            return saveRow(job, rowNumber, AdminCatalogJobRowOutcome.FAILED, "DUPLICATE_SLUG", "Product slug already exists: " + slug, payload);
        }

        Product product = new Product();
        product.setCategory(category);
        product.setName(name);
        product.setSlug(slug.toLowerCase(Locale.ROOT));
        product.setDescription(description);
        product.setActive(true);
        productRepository.save(product);

        return saveRow(job, rowNumber, AdminCatalogJobRowOutcome.SUCCESS, null, null, payload);
    }

    private CsvRowResult saveRow(
            AdminCatalogJob job,
            int rowNumber,
            AdminCatalogJobRowOutcome outcome,
            String errorCode,
            String errorMessage,
            String payload) {
        AdminCatalogJobRow row = new AdminCatalogJobRow();
        row.setJob(job);
        row.setRowNumber(rowNumber);
        row.setOutcome(outcome);
        row.setErrorCode(errorCode);
        row.setErrorMessage(errorMessage);
        row.setPayloadJson(payload);
        jobRowRepository.save(row);
        return new CsvRowResult(outcome);
    }

    private AdminCatalogJob newJob(String createdBy, AdminCatalogJobType type, AdminCatalogSourceFormat format) {
        AdminCatalogJob job = new AdminCatalogJob();
        job.setCreatedBy(createdBy == null || createdBy.isBlank() ? "system" : createdBy);
        job.setJobType(type);
        job.setSourceFormat(format);
        job.setStatus(AdminCatalogJobStatus.QUEUED);
        return job;
    }

    private void markFailed(AdminCatalogJob job, String summary) {
        job.setStatus(AdminCatalogJobStatus.FAILED);
        job.setSummary(summary);
        job.setCompletedAt(Instant.now());
    }

    private List<String> splitLines(String csvPayload) {
        String normalized = csvPayload.replace("\r\n", "\n").replace('\r', '\n');
        String[] rawLines = normalized.split("\n", -1);
        List<String> lines = new ArrayList<>();
        for (String line : rawLines) {
            if (!line.isBlank()) {
                lines.add(line);
            }
        }
        return lines;
    }

    private void ensureJobExists(UUID jobId) {
        if (!jobRepository.existsById(jobId)) {
            throw new ResourceNotFoundException("Catalog job not found");
        }
    }

    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        String escaped = value.replace("\"", "\"\"");
        if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }

    private void validateImportPayload(String csvPayload) {
        if (csvPayload == null || csvPayload.isBlank()) {
            throw new UnprocessableEntityException(
                    "Import payload is required",
                    List.of(new UnprocessableEntityException.FieldError("payload", "CSV payload must not be blank")));
        }
    }

    private record CsvRowResult(AdminCatalogJobRowOutcome outcome) {
    }
}
