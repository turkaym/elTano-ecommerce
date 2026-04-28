package com.eltano.ecommerce.orders.repository;

import java.util.UUID;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;

import jakarta.persistence.LockModeType;

import com.eltano.ecommerce.orders.domain.OrderDraft;

public interface OrderDraftRepository extends JpaRepository<OrderDraft, UUID> {

    boolean existsByReference(String reference);

    Optional<OrderDraft> findByReference(String reference);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select od from OrderDraft od where od.id = :id")
    Optional<OrderDraft> findByIdForUpdate(UUID id);

    @Query("""
            select od
            from OrderDraft od
            where (:status is null or od.status = :status)
              and (:fromInstant is null or od.createdAt >= :fromInstant)
              and (:toInstantExclusive is null or od.createdAt < :toInstantExclusive)
              and (:customer is null or lower(od.customerName) like lower(concat('%', :customer, '%')))
              and (:reference is null or lower(od.reference) like lower(concat('%', :reference, '%')))
            """)
    Page<OrderDraft> searchAdmin(
            com.eltano.ecommerce.orders.domain.OrderDraftStatus status,
            java.time.Instant fromInstant,
            java.time.Instant toInstantExclusive,
            String customer,
            String reference,
            Pageable pageable);
}
