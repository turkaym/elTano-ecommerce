package com.eltano.ecommerce.catalog.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.eltano.ecommerce.catalog.domain.Product;

public interface ProductRepository extends JpaRepository<Product, UUID> {

    boolean existsBySlugIgnoreCase(String slug);

    boolean existsBySlugIgnoreCaseAndIdNot(String slug, UUID id);

    long countByCategoryId(UUID categoryId);

    long countByCategoryIdAndActiveTrueAndDeletedAtIsNull(UUID categoryId);

    List<Product> findAllByCategoryId(UUID categoryId);

    @Query("""
            select distinct p
            from Product p
            join fetch p.category c
            left join fetch p.variants v
            where p.active = true
              and p.deletedAt is null
              and c.active = true
              and (v is null or v.active = true)
              and (:categorySlug is null or lower(c.slug) = :categorySlug)
            order by p.name asc
            """)
    List<Product> searchPublicCatalog(@Param("categorySlug") String categorySlug);

    @Query("""
            select distinct p
            from Product p
            join fetch p.category c
            left join fetch p.variants v
            order by p.createdAt desc
            """)
    List<Product> findAllWithRelations();

    @Query("""
            select distinct p
            from Product p
            join fetch p.category c
            left join fetch p.variants v
            where p.id = :id
            """)
    Optional<Product> findByIdWithRelations(@Param("id") UUID id);
}
