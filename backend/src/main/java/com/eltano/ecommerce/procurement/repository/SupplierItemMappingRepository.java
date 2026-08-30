package com.eltano.ecommerce.procurement.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import com.eltano.ecommerce.procurement.domain.SupplierItemMapping;

public interface SupplierItemMappingRepository extends JpaRepository<SupplierItemMapping, UUID> {
    boolean existsBySupplierIdAndNormalizedCode(UUID supplierId, String normalizedCode);
    boolean existsBySupplierIdAndNormalizedName(UUID supplierId, String normalizedName);
    Optional<SupplierItemMapping> findBySupplierIdAndNormalizedNameAndActiveTrue(UUID supplierId, String normalizedName);
    List<SupplierItemMapping> findAllBySupplierIdOrderBySupplierItemCode(UUID supplierId);
}
