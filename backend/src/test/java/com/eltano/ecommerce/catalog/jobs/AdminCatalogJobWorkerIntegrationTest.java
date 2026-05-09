package com.eltano.ecommerce.catalog.jobs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import io.micrometer.core.instrument.MeterRegistry;

import com.eltano.ecommerce.catalog.jobs.domain.AdminCatalogJob;
import com.eltano.ecommerce.catalog.jobs.domain.AdminCatalogJobStatus;
import com.eltano.ecommerce.catalog.jobs.domain.AdminCatalogJobType;
import com.eltano.ecommerce.catalog.jobs.domain.AdminCatalogSourceFormat;
import com.eltano.ecommerce.catalog.jobs.repository.AdminCatalogJobRepository;
import com.eltano.ecommerce.catalog.jobs.worker.AdminCatalogJobRetryPolicy;
import com.eltano.ecommerce.catalog.jobs.worker.AdminCatalogJobWorker;

@SpringBootTest(properties = {
        "app.catalog.seed-on-empty=false",
        "app.catalog.jobs.worker.enabled=true"
})
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class AdminCatalogJobWorkerIntegrationTest {

    @Autowired
    private AdminCatalogJobRepository repository;

    @Autowired
    private AdminCatalogJobWorker worker;

    @Autowired
    private MeterRegistry meterRegistry;

    @MockBean
    private AdminCatalogJobService jobService;

    @Test
    void workerEmitsCompletionTelemetryWithCorrelationIdInLogs(CapturedOutput output) {
        AdminCatalogJob queued = queuedJob(AdminCatalogJobType.EXPORT);
        when(jobService.executeClaimedJob(eq(queued.getId()))).thenReturn("processed=2,succeeded=2,failed=0");

        worker.runOnce();

        assertEquals(1.0, meterRegistry.counter("admin.catalog.jobs.claimed").count());
        assertEquals(1.0, meterRegistry.counter("admin.catalog.jobs.completed").count());
        assertTrue(meterRegistry.timer("admin.catalog.jobs.claim.latency").count() >= 1);
        assertTrue(meterRegistry.timer("admin.catalog.jobs.processing.duration").count() >= 1);
        assertTrue(output.getOut().contains("jobId=" + queued.getId()));
    }

    @Test
    void workerEmitsRetryAndTerminalFailureTelemetry(CapturedOutput output) {
        AdminCatalogJob retryable = queuedJob(AdminCatalogJobType.IMPORT);
        retryable.setMaxAttempts(3);
        repository.saveAndFlush(retryable);

        doThrow(new AdminCatalogJobRetryPolicy.RetryableJobException("temporary timeout"))
                .when(jobService)
                .executeClaimedJob(eq(retryable.getId()));

        worker.runOnce();

        assertEquals(1.0, meterRegistry.counter("admin.catalog.jobs.retried").count());
        assertTrue(output.getOut().contains("jobId=" + retryable.getId()));

        AdminCatalogJob terminal = queuedJob(AdminCatalogJobType.IMPORT);
        terminal.setMaxAttempts(1);
        repository.saveAndFlush(terminal);
        doThrow(new IllegalStateException("boom"))
                .when(jobService)
                .executeClaimedJob(eq(terminal.getId()));

        worker.runOnce();

        assertEquals(1.0, meterRegistry.counter("admin.catalog.jobs.failed.terminal").count());
        assertTrue(output.getOut().contains("jobId=" + terminal.getId()));
    }

    @BeforeEach
    void clean() {
        repository.deleteAll();
    }

    @Test
    void queuedJobTransitionsToCompletedAfterWorkerExecution() {
        AdminCatalogJob queued = queuedJob(AdminCatalogJobType.EXPORT);
        when(jobService.executeClaimedJob(eq(queued.getId()))).thenReturn("processed=1,succeeded=1,failed=0");

        assertEquals(AdminCatalogJobStatus.QUEUED, repository.findById(queued.getId()).orElseThrow().getStatus());

        worker.runOnce();

        AdminCatalogJob updated = repository.findById(queued.getId()).orElseThrow();
        assertEquals(AdminCatalogJobStatus.COMPLETED, updated.getStatus());
        assertEquals(1, updated.getAttemptCount());
        assertNotNull(updated.getStartedAt());
        assertNotNull(updated.getCompletedAt());
        assertEquals("processed=1,succeeded=1,failed=0", updated.getSummary());
        assertNull(updated.getLeasedBy());
        assertNull(updated.getLeasedUntil());
        assertNull(updated.getNextAttemptAt());
    }

    @Test
    void retryableFailureRequeuesJobWhenAttemptsRemain() {
        AdminCatalogJob queued = queuedJob(AdminCatalogJobType.IMPORT);
        queued.setMaxAttempts(3);
        repository.saveAndFlush(queued);

        doThrow(new AdminCatalogJobRetryPolicy.RetryableJobException("transient"))
                .when(jobService)
                .executeClaimedJob(eq(queued.getId()));

        worker.runOnce();

        AdminCatalogJob updated = repository.findById(queued.getId()).orElseThrow();
        assertEquals(AdminCatalogJobStatus.QUEUED, updated.getStatus());
        assertEquals(1, updated.getAttemptCount());
        assertTrue(updated.getLastError().contains("transient"));
        assertNull(updated.getLeasedBy());
        assertNull(updated.getLeasedUntil());
        assertNotNull(updated.getNextAttemptAt());
        assertTrue(updated.getNextAttemptAt().isAfter(Instant.now().minusSeconds(1)));
    }

    @Test
    void exhaustedAttemptsMarkJobAsTerminalFailed() {
        AdminCatalogJob queued = queuedJob(AdminCatalogJobType.IMPORT);
        queued.setMaxAttempts(1);
        repository.saveAndFlush(queued);

        doThrow(new IllegalStateException("permanent validation failure"))
                .when(jobService)
                .executeClaimedJob(eq(queued.getId()));

        worker.runOnce();

        AdminCatalogJob updated = repository.findById(queued.getId()).orElseThrow();
        assertEquals(AdminCatalogJobStatus.FAILED, updated.getStatus());
        assertEquals(1, updated.getAttemptCount());
        assertTrue(updated.getLastError().contains("permanent validation failure"));
        assertNotNull(updated.getCompletedAt());
        assertNull(updated.getLeasedBy());
        assertNull(updated.getLeasedUntil());
        assertNull(updated.getNextAttemptAt());
    }

    private AdminCatalogJob queuedJob(AdminCatalogJobType type) {
        AdminCatalogJob job = new AdminCatalogJob();
        job.setJobType(type);
        job.setStatus(AdminCatalogJobStatus.QUEUED);
        job.setCreatedBy("admin-user");
        job.setSourceFormat(AdminCatalogSourceFormat.CSV);
        job.setMaxAttempts(3);
        return repository.saveAndFlush(job);
    }
}
