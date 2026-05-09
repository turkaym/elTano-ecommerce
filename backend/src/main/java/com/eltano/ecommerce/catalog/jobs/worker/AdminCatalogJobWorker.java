package com.eltano.ecommerce.catalog.jobs.worker;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.eltano.ecommerce.catalog.jobs.AdminCatalogJobService;
import com.eltano.ecommerce.catalog.jobs.domain.AdminCatalogJob;
import com.eltano.ecommerce.catalog.jobs.domain.AdminCatalogJobStatus;
import com.eltano.ecommerce.catalog.jobs.repository.AdminCatalogJobRepository;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

@Component
@ConditionalOnProperty(value = "app.catalog.jobs.worker.enabled", havingValue = "true")
public class AdminCatalogJobWorker {

    private static final Logger log = LoggerFactory.getLogger(AdminCatalogJobWorker.class);

    private final AdminCatalogJobRepository repository;
    private final AdminCatalogJobService jobService;
    private final AdminCatalogJobRetryPolicy retryPolicy;
    private final MeterRegistry meterRegistry;
    private final String workerId;
    private final int leaseSeconds;
    private final boolean workerEnabled;

    public AdminCatalogJobWorker(
            AdminCatalogJobRepository repository,
            AdminCatalogJobService jobService,
            AdminCatalogJobRetryPolicy retryPolicy,
            MeterRegistry meterRegistry,
            @Value("${app.catalog.jobs.worker.enabled:false}") boolean workerEnabled,
            @Value("${app.catalog.jobs.worker.id:admin-catalog-worker}") String workerId,
            @Value("${app.catalog.jobs.worker.lease-seconds:30}") int leaseSeconds) {
        this.repository = repository;
        this.jobService = jobService;
        this.retryPolicy = retryPolicy;
        this.meterRegistry = meterRegistry;
        this.workerEnabled = workerEnabled;
        this.workerId = workerId;
        this.leaseSeconds = leaseSeconds;
    }

    @Scheduled(fixedDelayString = "${app.catalog.jobs.worker.poll-interval:1000ms}")
    public void scheduledRun() {
        runOnce();
    }

    @Transactional
    public void runOnce() {
        if (!workerEnabled) {
            return;
        }
        Instant now = Instant.now();
        meterRegistry.gauge("admin.catalog.jobs.queue.depth", repository.countByStatus(AdminCatalogJobStatus.QUEUED));
        Timer.Sample claimSample = Timer.start(meterRegistry);
        int claimed = claimNext(now);
        claimSample.stop(meterRegistry.timer("admin.catalog.jobs.claim.latency"));
        if (claimed == 0) {
            return;
        }
        meterRegistry.counter("admin.catalog.jobs.claimed").increment();

        Optional<AdminCatalogJob> claimedJob = repository.findFirstByStatusAndLeasedByOrderByUpdatedAtDesc(
                AdminCatalogJobStatus.PROCESSING,
                workerId);
        claimedJob.ifPresent(this::executeClaimed);
    }

    @Transactional
    protected int claimNext(Instant now) {
        return repository.claimNextEligibleJob(workerId, now, now.plusSeconds(leaseSeconds));
    }

    protected void executeClaimed(AdminCatalogJob job) {
        Timer.Sample processingSample = Timer.start(meterRegistry);
        try {
            String summary = jobService.executeClaimedJob(job.getId());
            complete(job.getId(), summary, Instant.now());
            meterRegistry.counter("admin.catalog.jobs.completed").increment();
            log.info("admin_catalog_job completed workerId={} jobId={} attempt={}", workerId, job.getId(), job.getAttemptCount());
        } catch (RuntimeException ex) {
            handleFailure(job, ex, Instant.now());
        } finally {
            processingSample.stop(meterRegistry.timer("admin.catalog.jobs.processing.duration"));
        }
    }

    @Transactional
    protected void complete(UUID jobId, String summary, Instant completedAt) {
        int updated = repository.markCompleted(jobId, workerId, summary, completedAt);
        if (updated == 0) {
            log.warn("admin_catalog_job complete_skipped workerId={} jobId={}", workerId, jobId);
        }
    }

    @Transactional
    protected void handleFailure(AdminCatalogJob job, RuntimeException error, Instant now) {
        String message = error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
        if (retryPolicy.shouldRetry(job.getAttemptCount(), job.getMaxAttempts(), error)) {
            int updated = repository.releaseClaimForRetry(job.getId(), workerId, message,
                    retryPolicy.nextAttemptAt(now, job.getAttemptCount()));
            if (updated == 0) {
                log.warn("admin_catalog_job retry_skipped workerId={} jobId={} attempt={} error={}",
                        workerId,
                        job.getId(),
                        job.getAttemptCount(),
                        message);
                return;
            }
            meterRegistry.counter("admin.catalog.jobs.retried").increment();
            log.warn("admin_catalog_job retried workerId={} jobId={} attempt={} error={}", workerId, job.getId(), job.getAttemptCount(), message);
            return;
        }
        int updated = repository.markFailed(job.getId(), workerId, message, now);
        if (updated == 0) {
            log.warn("admin_catalog_job fail_skipped workerId={} jobId={} attempt={} error={}",
                    workerId,
                    job.getId(),
                    job.getAttemptCount(),
                    message);
            return;
        }
        meterRegistry.counter("admin.catalog.jobs.failed.terminal").increment();
        log.error("admin_catalog_job failed_terminal workerId={} jobId={} attempt={} error={}", workerId, job.getId(), job.getAttemptCount(), message);
    }
}
