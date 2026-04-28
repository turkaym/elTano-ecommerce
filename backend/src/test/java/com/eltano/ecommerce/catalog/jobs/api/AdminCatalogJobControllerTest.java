package com.eltano.ecommerce.catalog.jobs.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import com.eltano.ecommerce.catalog.jobs.AdminCatalogJobService;
import com.eltano.ecommerce.catalog.jobs.domain.AdminCatalogJob;
import com.eltano.ecommerce.catalog.jobs.domain.AdminCatalogJobRow;
import com.eltano.ecommerce.catalog.jobs.domain.AdminCatalogJobRowOutcome;
import com.eltano.ecommerce.catalog.jobs.domain.AdminCatalogJobStatus;
import com.eltano.ecommerce.catalog.jobs.domain.AdminCatalogJobType;
import com.eltano.ecommerce.catalog.jobs.domain.AdminCatalogSourceFormat;
import com.eltano.ecommerce.audit.service.AdminAuditService;
import com.eltano.ecommerce.common.api.RestExceptionHandler;
import com.eltano.ecommerce.common.api.CancellationNotSupportedException;
import com.eltano.ecommerce.common.api.UnprocessableEntityException;
import com.eltano.ecommerce.config.SecurityConfig;

@WebMvcTest(controllers = AdminCatalogJobController.class)
@Import({ SecurityConfig.class, RestExceptionHandler.class })
class AdminCatalogJobControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AdminCatalogJobService adminCatalogJobService;

    @MockBean
    private AdminAuditService adminAuditService;

    @Test
    void importPostReturnsAcceptedQueuedContract() throws Exception {
        UUID jobId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        when(adminCatalogJobService.enqueueCsvImport(eq("admin-user"), any(String.class)))
                .thenReturn(job(jobId, AdminCatalogJobType.IMPORT, AdminCatalogJobStatus.QUEUED, null));

        mockMvc.perform(post("/api/admin/catalog/jobs/import")
                .contentType(MediaType.TEXT_PLAIN)
                .content("categorySlug,name,slug,description\\nfrutos-secos,Almendra,almendra-1,Desc")
                .with(httpBasic("admin-user", "admin-pass"))
                .with(csrf()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value(jobId.toString()))
                .andExpect(jsonPath("$.jobType").value("IMPORT"))
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.createdBy").value("admin-user"))
                .andExpect(jsonPath("$.sourceFormat").value("CSV"));
    }

    @Test
    void exportPostReturnsAcceptedQueuedContract() throws Exception {
        UUID jobId = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
        when(adminCatalogJobService.enqueueCsvExport(eq("admin-user")))
                .thenReturn(job(jobId, AdminCatalogJobType.EXPORT, AdminCatalogJobStatus.QUEUED, null));

        mockMvc.perform(post("/api/admin/catalog/jobs/export")
                .with(httpBasic("admin-user", "admin-pass"))
                .with(csrf()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value(jobId.toString()))
                .andExpect(jsonPath("$.jobType").value("EXPORT"))
                .andExpect(jsonPath("$.status").value("QUEUED"));
    }

    @Test
    void getAndReportEndpointsKeepCompatibilityWhileQueued() throws Exception {
        UUID jobId = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");
        when(adminCatalogJobService.getJob(jobId)).thenReturn(job(jobId, AdminCatalogJobType.IMPORT, AdminCatalogJobStatus.QUEUED, null));
        when(adminCatalogJobService.listRows(jobId)).thenReturn(List.of(row(2, AdminCatalogJobRowOutcome.FAILED, "INVALID_ROW", "Expected 4 columns")));
        when(adminCatalogJobService.validationReportCsv(jobId)).thenReturn("rowNumber,errorCode,errorMessage,payload\n2,INVALID_ROW,Expected 4 columns,bad");
        when(adminCatalogJobService.exportCsv(jobId)).thenReturn("categorySlug,name,slug,description\nfrutos-secos,Almendra,almendra-1,Desc");

        mockMvc.perform(get("/api/admin/catalog/jobs/{id}", jobId)
                .with(httpBasic("admin-user", "admin-pass")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(jobId.toString()))
                .andExpect(jsonPath("$.status").value("QUEUED"));

        mockMvc.perform(get("/api/admin/catalog/jobs/{id}/rows", jobId)
                .with(httpBasic("admin-user", "admin-pass")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].rowNumber").value(2))
                .andExpect(jsonPath("$[0].outcome").value("FAILED"));

        mockMvc.perform(get("/api/admin/catalog/jobs/{id}/report", jobId)
                .with(httpBasic("admin-user", "admin-pass")))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/csv"))
                .andExpect(content().string("rowNumber,errorCode,errorMessage,payload\n2,INVALID_ROW,Expected 4 columns,bad"));

        mockMvc.perform(get("/api/admin/catalog/jobs/{id}/export-file", jobId)
                .with(httpBasic("admin-user", "admin-pass")))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/csv"))
                .andExpect(content().string("categorySlug,name,slug,description\nfrutos-secos,Almendra,almendra-1,Desc"));
    }

    @Test
    void cancelReturnsNotImplementedWithMachineReadableReason() throws Exception {
        UUID jobId = UUID.fromString("dddddddd-dddd-dddd-dddd-dddddddddddd");
        doThrow(new CancellationNotSupportedException("Cancellation is not supported for admin catalog jobs in this phase."))
                .when(adminCatalogJobService)
                .cancelJob(jobId);

        mockMvc.perform(post("/api/admin/catalog/jobs/{id}/cancel", jobId)
                .with(httpBasic("admin-user", "admin-pass"))
                .with(csrf()))
                .andExpect(status().isNotImplemented())
                .andExpect(jsonPath("$.code").value(CancellationNotSupportedException.CODE))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void invalidImportPayloadReturnsValidationErrorAndDoesNotEnqueueJob() throws Exception {
        when(adminCatalogJobService.enqueueCsvImport(eq("admin-user"), any(String.class)))
                .thenThrow(new UnprocessableEntityException(
                        "Import payload is required",
                        List.of(new UnprocessableEntityException.FieldError("payload", "CSV payload must not be blank"))));

        mockMvc.perform(post("/api/admin/catalog/jobs/import")
                .contentType(MediaType.TEXT_PLAIN)
                .content("   ")
                .with(httpBasic("admin-user", "admin-pass"))
                .with(csrf()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("UNPROCESSABLE_ENTITY"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("payload"));
    }

    @Test
    void invalidExportFormatReturnsBadRequestAndDoesNotEnqueueJob() throws Exception {
        mockMvc.perform(post("/api/admin/catalog/jobs/export?format=json")
                .with(httpBasic("admin-user", "admin-pass"))
                .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));

        verify(adminCatalogJobService, never()).enqueueCsvExport(any());
    }

    @Test
    void failedJobRemainsQueryableWithFailureDetails() throws Exception {
        UUID jobId = UUID.fromString("eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee");
        when(adminCatalogJobService.getJob(jobId)).thenReturn(job(jobId, AdminCatalogJobType.IMPORT, AdminCatalogJobStatus.FAILED,
                "processed=3,succeeded=2,failed=1"));
        when(adminCatalogJobService.listRows(jobId)).thenReturn(List.of(
                row(3, AdminCatalogJobRowOutcome.FAILED, "INVALID_ROW", "Expected 4 columns")));
        when(adminCatalogJobService.validationReportCsv(jobId))
                .thenReturn("rowNumber,errorCode,errorMessage,payload\n3,INVALID_ROW,Expected 4 columns,bad");

        mockMvc.perform(get("/api/admin/catalog/jobs/{id}", jobId)
                .with(httpBasic("admin-user", "admin-pass")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(jobId.toString()))
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.summary").value("processed=3,succeeded=2,failed=1"));

        mockMvc.perform(get("/api/admin/catalog/jobs/{id}/rows", jobId)
                .with(httpBasic("admin-user", "admin-pass")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].outcome").value("FAILED"))
                .andExpect(jsonPath("$[0].errorCode").value("INVALID_ROW"))
                .andExpect(jsonPath("$[0].errorMessage").value("Expected 4 columns"));

        mockMvc.perform(get("/api/admin/catalog/jobs/{id}/report", jobId)
                .with(httpBasic("admin-user", "admin-pass")))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/csv"))
                .andExpect(content().string("rowNumber,errorCode,errorMessage,payload\n3,INVALID_ROW,Expected 4 columns,bad"));
    }

    private AdminCatalogJob job(UUID id, AdminCatalogJobType type, AdminCatalogJobStatus status, String summary) {
        AdminCatalogJob job = new AdminCatalogJob();
        ReflectionTestUtils.setField(job, "id", id);
        job.setJobType(type);
        job.setSourceFormat(AdminCatalogSourceFormat.CSV);
        job.setCreatedBy("admin-user");
        job.setStatus(status);
        job.setSummary(summary);
        ReflectionTestUtils.setField(job, "createdAt", Instant.parse("2026-04-28T14:00:00Z"));
        ReflectionTestUtils.setField(job, "updatedAt", Instant.parse("2026-04-28T14:00:00Z"));
        return job;
    }

    private AdminCatalogJobRow row(int rowNumber, AdminCatalogJobRowOutcome outcome, String errorCode, String errorMessage) {
        AdminCatalogJobRow row = new AdminCatalogJobRow();
        ReflectionTestUtils.setField(row, "id", UUID.randomUUID());
        row.setRowNumber(rowNumber);
        row.setOutcome(outcome);
        row.setErrorCode(errorCode);
        row.setErrorMessage(errorMessage);
        row.setPayloadJson("bad");
        ReflectionTestUtils.setField(row, "createdAt", Instant.parse("2026-04-28T14:01:00Z"));
        return row;
    }
}
