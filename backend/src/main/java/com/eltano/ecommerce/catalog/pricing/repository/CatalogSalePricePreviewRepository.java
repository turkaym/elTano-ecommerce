package com.eltano.ecommerce.catalog.pricing.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.eltano.ecommerce.catalog.pricing.domain.CatalogSalePricePreview;

import jakarta.persistence.LockModeType;

public interface CatalogSalePricePreviewRepository extends JpaRepository<CatalogSalePricePreview, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from CatalogSalePricePreview p where p.id = :id")
    Optional<CatalogSalePricePreview> findByIdForUpdate(@Param("id") UUID id);

    Optional<CatalogSalePricePreview> findByConfirmIdempotencyKey(String confirmIdempotencyKey);
}
