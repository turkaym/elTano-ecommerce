package com.eltano.ecommerce.catalog.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.eltano.ecommerce.catalog.domain.Category;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

    Optional<Category> findBySlugIgnoreCase(String slug);

    boolean existsBySlugIgnoreCase(String slug);

    boolean existsBySlugIgnoreCaseAndIdNot(String slug, UUID id);
}
