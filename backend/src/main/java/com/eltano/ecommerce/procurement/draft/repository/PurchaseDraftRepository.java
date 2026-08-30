package com.eltano.ecommerce.procurement.draft.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.eltano.ecommerce.procurement.draft.domain.PurchaseDraft;
import com.eltano.ecommerce.procurement.draft.domain.PurchaseDraftStatus;

import jakarta.persistence.LockModeType;

public interface PurchaseDraftRepository extends JpaRepository<PurchaseDraft, UUID> {
    @EntityGraph(attributePaths = {"supplier", "lines"})
    List<PurchaseDraft> findAllByStatusNotOrderByUpdatedAtDesc(PurchaseDraftStatus status);
    @EntityGraph(attributePaths = {"supplier", "lines"})
    @Query("select d from PurchaseDraft d where d.id = :id")
    Optional<PurchaseDraft> findDetailedById(@Param("id") UUID id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from PurchaseDraft d where d.id = :id")
    Optional<PurchaseDraft> findByIdForUpdate(@Param("id") UUID id);
    @EntityGraph(attributePaths = {"supplier", "lines"})
    Optional<PurchaseDraft> findBySupplierIdAndSourceSha256AndStatusNot(UUID supplierId, String hash, PurchaseDraftStatus status);
}
