package com.eltano.ecommerce.procurement;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class PurchaseDraftMigrationContractTest {
    private static final Path MIGRATION = Path.of("src/main/resources/db/migration/V1_14__procurement_purchase_drafts.sql");

    @Test
    void definesDraftVersionSourceEvidenceStatusesAndSupplierScopedUniqueness() throws Exception {
        String sql = Files.readString(MIGRATION).toLowerCase();
        assertTrue(sql.contains("create table purchase_drafts"));
        assertTrue(sql.contains("version bigint"));
        assertTrue(sql.contains("'draft', 'confirmed', 'deleted'"));
        assertTrue(sql.contains("source_sha256 varchar(64)"));
        assertTrue(sql.contains("uk_purchase_draft_supplier_source_hash"));
        assertTrue(sql.contains("confirmed_purchase_id uuid references purchases"));
        assertTrue(sql.contains("confirmed_receipt_id uuid references purchase_receipts"));
        assertTrue(sql.contains("source_date date"));
        assertTrue(sql.contains("create table purchase_draft_import_keys"));
        assertTrue(sql.contains("unique (supplier_id, idempotency_key)"));
    }

    @Test
    void evolvesMappingsAndProtectsConfirmedDraftAndLines() throws Exception {
        String sql = Files.readString(MIGRATION).toLowerCase();
        assertTrue(sql.contains("supplier_item_name"));
        assertTrue(sql.contains("normalized_name"));
        assertTrue(sql.contains("supplier_mapping_identifier_check"));
        assertTrue(sql.contains("uk_supplier_mapping_name"));
        assertTrue(sql.contains("confirmed purchase drafts are immutable"));
        assertTrue(sql.contains("confirmed purchase draft lines are immutable"));
    }

    @Test
    void productionImageOwnsTheExactPurchaseStorageMount() throws Exception {
        String dockerfile = Files.readString(Path.of("Dockerfile"));
        String compose = Files.readString(Path.of("../docker-compose.prod.yml"));
        String path = "/app/uploads/private/purchases";
        assertTrue(dockerfile.contains("mkdir -p /app/uploads/product-images " + path));
        assertTrue(dockerfile.contains("VOLUME [\"/app/uploads/product-images\", \"" + path + "\"]"));
        assertTrue(dockerfile.indexOf("chown -R appuser:appuser /app") < dockerfile.indexOf("USER appuser"));
        assertTrue(compose.contains("PURCHASE_FILE_STORAGE_DIR: " + path));
        assertTrue(compose.contains("purchase-files:" + path));
    }
}
