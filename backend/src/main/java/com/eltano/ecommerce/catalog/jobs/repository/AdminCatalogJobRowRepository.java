package com.eltano.ecommerce.catalog.jobs.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.eltano.ecommerce.catalog.jobs.domain.AdminCatalogJobRow;

public interface AdminCatalogJobRowRepository extends JpaRepository<AdminCatalogJobRow, UUID> {

    List<AdminCatalogJobRow> findByJobIdOrderByRowNumberAsc(UUID jobId);
}
