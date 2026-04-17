package com.eltano.ecommerce.catalog.repository;

import java.util.UUID;
import java.util.List;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;

import jakarta.persistence.LockModeType;

import com.eltano.ecommerce.catalog.domain.ProductVariant;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, UUID> {

    boolean existsBySkuIgnoreCase(String sku);

    boolean existsBySkuIgnoreCaseAndIdNot(String sku, UUID id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select pv from ProductVariant pv where pv.id in :ids")
    List<ProductVariant> findAllByIdInForUpdate(List<UUID> ids);
}
