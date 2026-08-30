package com.eltano.ecommerce.procurement.draft.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.eltano.ecommerce.procurement.draft.domain.PurchaseDraftImportKey;

public interface PurchaseDraftImportKeyRepository extends JpaRepository<PurchaseDraftImportKey, UUID> {
    Optional<PurchaseDraftImportKey> findBySupplierIdAndIdempotencyKey(UUID supplierId, String idempotencyKey);
}
