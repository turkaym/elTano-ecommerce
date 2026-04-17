package com.eltano.ecommerce.orders.repository;

import java.util.UUID;
import java.util.Optional;

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
}
