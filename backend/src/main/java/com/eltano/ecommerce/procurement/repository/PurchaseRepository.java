package com.eltano.ecommerce.procurement.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.eltano.ecommerce.procurement.domain.Purchase;
import com.eltano.ecommerce.procurement.domain.PurchaseStatus;
import jakarta.persistence.LockModeType;

public interface PurchaseRepository extends JpaRepository<Purchase, UUID> {
    boolean existsBySupplierIdAndNormalizedDocumentTypeAndNormalizedDocumentNumber(UUID supplierId, String type, String number);
    @EntityGraph(attributePaths = {"supplier", "lines"}) List<Purchase> findAllByOrderByPurchasedAtDesc();
    @EntityGraph(attributePaths = {"supplier", "lines"}) List<Purchase> findAllByStatusOrderByPurchasedAtDesc(PurchaseStatus status);
    @EntityGraph(attributePaths = {"supplier", "lines"}) List<Purchase> findAllBySupplierIdOrderByPurchasedAtDesc(UUID supplierId);
    @EntityGraph(attributePaths = {"supplier", "lines"}) List<Purchase> findAllBySupplierIdAndStatusOrderByPurchasedAtDesc(UUID supplierId, PurchaseStatus status);
    @EntityGraph(attributePaths = {"supplier", "lines"})
    @Query("select p from Purchase p where p.id = :id")
    Optional<Purchase> findDetailedById(@Param("id") UUID id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Purchase p join fetch p.supplier left join fetch p.lines where p.id = :id")
    Optional<Purchase> findByIdForUpdate(@Param("id") UUID id);
}
