package com.eltano.ecommerce.catalog.repository;

import java.util.UUID;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import com.eltano.ecommerce.catalog.domain.ProductVariant;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, UUID> {

    boolean existsBySkuIgnoreCase(String sku);

    boolean existsBySkuIgnoreCaseAndIdNot(String sku, UUID id);

    @Query("""
            select distinct pv
            from ProductVariant pv
            join fetch pv.product p
            join fetch p.category c
            left join fetch p.variants v
            where lower(pv.sku) = lower(:sku)
            """)
    Optional<ProductVariant> findBySkuIgnoreCaseWithProduct(@Param("sku") String sku);

    @Query("""
            select distinct pv
            from ProductVariant pv
            join fetch pv.product p
            join fetch p.category c
            left join fetch p.variants v
            where lower(pv.sku) in :skus
            """)
    List<ProductVariant> findBySkuLowercaseInWithProduct(@Param("skus") List<String> skus);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select pv from ProductVariant pv where pv.id in :ids")
    List<ProductVariant> findAllByIdInForUpdate(List<UUID> ids);
}
