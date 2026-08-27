package com.eltano.ecommerce.procurement;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class ProcurementMigrationContractTest {
    private static final Path MIGRATION = Path.of("src/main/resources/db/migration/V1_13__procurement_receiving.sql");

    @Test
    void migrationDefinesNormalizedIdentityTargetXorAndPositiveExactQuantities() throws Exception {
        String sql = Files.readString(MIGRATION).toLowerCase();
        assertTrue(sql.contains("lower(btrim(document_type))"));
        assertTrue(sql.contains("lower(btrim(document_number))"));
        assertTrue(sql.contains("check ((target_type = 'variant_unit'"));
        assertTrue(sql.contains("numeric(18,6)"));
        assertTrue(sql.contains("check (ordered_quantity > 0)"));
    }

    @Test
    void migrationProtectsReplayMovementIdentityAndConfirmedEvidence() throws Exception {
        String sql = Files.readString(MIGRATION).toLowerCase();
        assertTrue(sql.contains("unique (purchase_id, kind, idempotency_key)"));
        assertTrue(sql.contains("unique (source_type, source_id)"));
        assertTrue(sql.contains("raise exception 'confirmed procurement evidence is immutable'"));
        assertTrue(sql.contains("purchase_status_check"));
    }
}
