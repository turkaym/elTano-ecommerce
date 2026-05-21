package com.eltano.ecommerce.catalog.jobs.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.eltano.ecommerce.catalog.jobs.domain.AdminCatalogJob;
import com.eltano.ecommerce.catalog.jobs.domain.AdminCatalogJobStatus;
import com.eltano.ecommerce.catalog.jobs.domain.AdminCatalogJobType;
import com.eltano.ecommerce.catalog.jobs.domain.AdminCatalogSourceFormat;

@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AdminCatalogJobRepositoryIntegrationTest {

    @Autowired
    private AdminCatalogJobRepository repository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void claimNextEligibleJobIsAtomicUnderContention() throws Exception {
        AdminCatalogJob queued = new AdminCatalogJob();
        queued.setJobType(AdminCatalogJobType.IMPORT);
        queued.setStatus(AdminCatalogJobStatus.QUEUED);
        queued.setCreatedBy("admin-user");
        queued.setSourceFormat(AdminCatalogSourceFormat.CSV);
        queued.setMaxAttempts(3);
        repository.saveAndFlush(queued);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Instant now = Instant.now();
            Instant leaseUntil = now.plusSeconds(30);
            Callable<Integer> claimByA = () -> new TransactionTemplate(transactionManager)
                    .execute(status -> repository.claimNextEligibleJob("worker-a", now, leaseUntil));
            Callable<Integer> claimByB = () -> new TransactionTemplate(transactionManager)
                    .execute(status -> repository.claimNextEligibleJob("worker-b", now, leaseUntil));

            Future<Integer> a = pool.submit(claimByA);
            Future<Integer> b = pool.submit(claimByB);

            int claims = a.get() + b.get();
            assertEquals(1, claims);

            Optional<AdminCatalogJob> claimedByA = repository.findFirstByStatusAndLeasedByOrderByUpdatedAtDesc(
                    AdminCatalogJobStatus.PROCESSING, "worker-a");
            Optional<AdminCatalogJob> claimedByB = repository.findFirstByStatusAndLeasedByOrderByUpdatedAtDesc(
                    AdminCatalogJobStatus.PROCESSING, "worker-b");

            assertTrue(claimedByA.isPresent() || claimedByB.isPresent());
            AdminCatalogJob claimed = claimedByA.orElseGet(claimedByB::orElseThrow);
            assertEquals(AdminCatalogJobStatus.PROCESSING, claimed.getStatus());
            assertEquals(1, claimed.getAttemptCount());
            assertNotNull(claimed.getLeasedUntil());
            assertNotNull(claimed.getStartedAt());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void expiredLeaseCanBeReclaimedByAnotherWorker() {
        AdminCatalogJob stuck = new AdminCatalogJob();
        stuck.setJobType(AdminCatalogJobType.EXPORT);
        stuck.setStatus(AdminCatalogJobStatus.QUEUED);
        stuck.setCreatedBy("admin-user");
        stuck.setSourceFormat(AdminCatalogSourceFormat.CSV);
        stuck.setMaxAttempts(3);
        stuck.setLeasedBy("worker-old");
        stuck.setLeasedUntil(Instant.now().minusSeconds(5));
        UUID id = repository.saveAndFlush(stuck).getId();

        Instant now = Instant.now();
        int claimCount = new TransactionTemplate(transactionManager)
                .execute(status -> repository.claimNextEligibleJob("worker-new", now, now.plusSeconds(45)));
        assertEquals(1, claimCount);

        AdminCatalogJob reclaimed = repository.findById(id).orElseThrow();
        assertEquals(AdminCatalogJobStatus.PROCESSING, reclaimed.getStatus());
        assertEquals("worker-new", reclaimed.getLeasedBy());
        assertEquals(1, reclaimed.getAttemptCount());
        assertTrue(reclaimed.getLeasedUntil().isAfter(now));
    }

    @Test
    void terminalJobsAreNeverReclaimedByClaimLoop() {
        AdminCatalogJob completed = new AdminCatalogJob();
        completed.setJobType(AdminCatalogJobType.EXPORT);
        completed.setStatus(AdminCatalogJobStatus.COMPLETED);
        completed.setCreatedBy("admin-user");
        completed.setSourceFormat(AdminCatalogSourceFormat.CSV);
        completed.setMaxAttempts(3);
        completed.setLeasedBy("worker-old");
        completed.setLeasedUntil(Instant.now().minusSeconds(30));
        repository.saveAndFlush(completed);

        AdminCatalogJob failed = new AdminCatalogJob();
        failed.setJobType(AdminCatalogJobType.IMPORT);
        failed.setStatus(AdminCatalogJobStatus.FAILED);
        failed.setCreatedBy("admin-user");
        failed.setSourceFormat(AdminCatalogSourceFormat.CSV);
        failed.setMaxAttempts(1);
        failed.setAttemptCount(1);
        failed.setLeasedBy("worker-old");
        failed.setLeasedUntil(Instant.now().minusSeconds(30));
        repository.saveAndFlush(failed);

        Instant now = Instant.now();
        int claimCount = new TransactionTemplate(transactionManager)
                .execute(status -> repository.claimNextEligibleJob("worker-new", now, now.plusSeconds(45)));

        assertEquals(0, claimCount);
        assertEquals(AdminCatalogJobStatus.COMPLETED, repository.findById(completed.getId()).orElseThrow().getStatus());
        assertEquals(AdminCatalogJobStatus.FAILED, repository.findById(failed.getId()).orElseThrow().getStatus());
    }
}
