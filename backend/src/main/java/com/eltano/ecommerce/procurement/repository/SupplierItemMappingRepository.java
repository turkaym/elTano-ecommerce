package com.eltano.ecommerce.procurement.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.eltano.ecommerce.procurement.domain.SupplierItemMapping;
import jakarta.persistence.LockModeType;

public interface SupplierItemMappingRepository extends JpaRepository<SupplierItemMapping, UUID> {
    boolean existsBySupplierIdAndNormalizedCode(UUID supplierId, String normalizedCode);
    boolean existsBySupplierIdAndNormalizedName(UUID supplierId, String normalizedName);
    Optional<SupplierItemMapping> findBySupplierIdAndNormalizedNameAndActiveTrue(UUID supplierId, String normalizedName);
    Optional<SupplierItemMapping> findBySupplierIdAndNormalizedName(UUID supplierId, String normalizedName);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from SupplierItemMapping m where m.supplier.id = :supplierId and m.normalizedName = :normalizedName")
    Optional<SupplierItemMapping> findBySupplierIdAndNormalizedNameForUpdate(@Param("supplierId") UUID supplierId,
            @Param("normalizedName") String normalizedName);
    List<SupplierItemMapping> findAllBySupplierIdOrderBySupplierItemCode(UUID supplierId);
}
