package com.eltano.ecommerce.catalog.jobs.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.eltano.ecommerce.catalog.jobs.domain.AdminCatalogJobInput;

public interface AdminCatalogJobInputRepository extends JpaRepository<AdminCatalogJobInput, UUID> {
}
