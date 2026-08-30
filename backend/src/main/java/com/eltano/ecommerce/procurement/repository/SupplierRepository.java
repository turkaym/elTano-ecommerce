package com.eltano.ecommerce.procurement.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.eltano.ecommerce.procurement.domain.Supplier;
import jakarta.persistence.LockModeType;

public interface SupplierRepository extends JpaRepository<Supplier, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Supplier s where s.id = :id")
    java.util.Optional<Supplier> findByIdForUpdate(@Param("id") UUID id);
}
