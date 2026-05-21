package com.eltano.ecommerce.catalog.jobs.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.eltano.ecommerce.catalog.jobs.domain.AdminCatalogJob;
import com.eltano.ecommerce.catalog.jobs.domain.AdminCatalogJobStatus;

public interface AdminCatalogJobRepository extends JpaRepository<AdminCatalogJob, UUID> {

    String STATUS_QUEUED = "QUEUED";
    String STATUS_PROCESSING = "PROCESSING";

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            update admin_catalog_jobs
            set status = 'PROCESSING',
                leased_by = :leasedBy,
                leased_until = :leasedUntil,
                attempt_count = attempt_count + 1,
                started_at = coalesce(started_at, :now),
                next_attempt_at = null
            where id = (
                select id
                from admin_catalog_jobs
                where status = 'QUEUED'
                  and (next_attempt_at is null or next_attempt_at <= :now)
                  and (leased_until is null or leased_until < :now)
                order by created_at asc
                limit 1
            )
            and status = 'QUEUED'
            and (leased_until is null or leased_until < :now)
            """, nativeQuery = true)
    int claimNextEligibleJob(@Param("leasedBy") String leasedBy,
            @Param("now") Instant now,
            @Param("leasedUntil") Instant leasedUntil);

    Optional<AdminCatalogJob> findFirstByStatusAndLeasedByOrderByUpdatedAtDesc(AdminCatalogJobStatus status, String leasedBy);

    List<AdminCatalogJob> findAllByOrderByCreatedAtDescIdDesc();

    long countByStatus(AdminCatalogJobStatus status);

    @Query("""
            select j
            from AdminCatalogJob j
            where j.status = com.eltano.ecommerce.catalog.jobs.domain.AdminCatalogJobStatus.QUEUED
              and (j.nextAttemptAt is null or j.nextAttemptAt <= :now)
              and (j.leasedUntil is null or j.leasedUntil < :now)
            order by j.createdAt asc
            """)
    List<AdminCatalogJob> findEligibleQueuedJobs(@Param("now") Instant now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AdminCatalogJob j
            set j.status = com.eltano.ecommerce.catalog.jobs.domain.AdminCatalogJobStatus.QUEUED,
                j.leasedBy = null,
                j.leasedUntil = null,
                j.lastError = :lastError,
                j.nextAttemptAt = :nextAttemptAt
            where j.id = :jobId
              and j.status = com.eltano.ecommerce.catalog.jobs.domain.AdminCatalogJobStatus.PROCESSING
              and j.leasedBy = :leasedBy
            """)
    int releaseClaimForRetry(@Param("jobId") UUID jobId,
            @Param("leasedBy") String leasedBy,
            @Param("lastError") String lastError,
            @Param("nextAttemptAt") Instant nextAttemptAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AdminCatalogJob j
            set j.status = com.eltano.ecommerce.catalog.jobs.domain.AdminCatalogJobStatus.COMPLETED,
                j.summary = :summary,
                j.completedAt = :completedAt,
                j.leasedBy = null,
                j.leasedUntil = null,
                j.lastError = null,
                j.nextAttemptAt = null
            where j.id = :jobId
              and j.status = com.eltano.ecommerce.catalog.jobs.domain.AdminCatalogJobStatus.PROCESSING
              and j.leasedBy = :leasedBy
            """)
    int markCompleted(@Param("jobId") UUID jobId,
            @Param("leasedBy") String leasedBy,
            @Param("summary") String summary,
            @Param("completedAt") Instant completedAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update AdminCatalogJob j
            set j.status = com.eltano.ecommerce.catalog.jobs.domain.AdminCatalogJobStatus.FAILED,
                j.lastError = :lastError,
                j.completedAt = :completedAt,
                j.leasedBy = null,
                j.leasedUntil = null,
                j.nextAttemptAt = null
            where j.id = :jobId
              and j.status = com.eltano.ecommerce.catalog.jobs.domain.AdminCatalogJobStatus.PROCESSING
              and j.leasedBy = :leasedBy
            """)
    int markFailed(@Param("jobId") UUID jobId,
            @Param("leasedBy") String leasedBy,
            @Param("lastError") String lastError,
            @Param("completedAt") Instant completedAt);
}
