package com.eltano.ecommerce.catalog.pricing;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class CatalogSalePriceMigrationContractTest {
    @Test
    void migrationPersistsPreviewSnapshotAndUniqueConfirmationEvidence() throws Exception {
        String migration = Files.readString(Path.of(
                "src/main/resources/db/migration/V1_17__catalog_sale_price_previews.sql"));

        assertThat(migration).contains("catalog_sale_price_previews", "snapshot_json text NOT NULL",
                "preview_hash varchar(64) NOT NULL", "confirm_idempotency_key varchar(180)",
                "CREATE UNIQUE INDEX uk_catalog_sale_price_confirm_key");
    }
}
