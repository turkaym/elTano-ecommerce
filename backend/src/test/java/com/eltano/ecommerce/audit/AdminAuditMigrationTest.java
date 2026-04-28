package com.eltano.ecommerce.audit;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class AdminAuditMigrationTest {

    @Test
    void migrationV16DefinesAdminAuditAndJobTables() throws Exception {
        String migration = Files.readString(Path.of("src/main/resources/db/migration/V1_6__admin_audit_and_jobs.sql"));

        assertTrue(migration.contains("create table if not exists admin_audit_events"));
        assertTrue(migration.contains("correlation_id"));
        assertTrue(migration.contains("create table if not exists admin_catalog_jobs"));
        assertTrue(migration.contains("create table if not exists admin_catalog_job_rows"));
    }
}
