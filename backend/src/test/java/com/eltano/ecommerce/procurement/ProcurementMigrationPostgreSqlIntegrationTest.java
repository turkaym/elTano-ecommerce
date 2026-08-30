package com.eltano.ecommerce.procurement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class ProcurementMigrationPostgreSqlIntegrationTest {
    static {
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        boolean dockerHostMissing = System.getenv("DOCKER_HOST") == null && System.getProperty("docker.host") == null;
        if (windows && dockerHostMissing) System.setProperty("docker.host", "npipe:////./pipe/dockerDesktopLinuxEngine");
    }

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @BeforeAll
    static void migrate() {
        var result = Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
        assertEquals(15, result.migrationsExecuted);
    }

    @Test
    void enforcesNormalizedUniquenessTargetXorPositiveQuantitiesAndLifecycle() throws Exception {
        try (Connection connection = POSTGRES.createConnection("")) {
            Fixture fixture = fixture(connection);

            assertConstraint(connection, "insert into suppliers(id,name) values ('%s','Duplicate')".formatted(fixture.supplierId()));
            assertConstraint(connection, "insert into supplier_item_mappings(id,supplier_id,supplier_item_code,normalized_code,description,target_type,product_id,variant_id,default_conversion) values ('%s','%s',' Code ','code','bad','VARIANT_UNIT','%s','%s',1)"
                    .formatted(UUID.randomUUID(), fixture.supplierId(), fixture.productId(), fixture.variantId()));
            assertConstraint(connection, "insert into supplier_item_mappings(id,supplier_id,supplier_item_code,normalized_code,description,target_type,variant_id,default_conversion) values ('%s','%s','Other','other','bad','VARIANT_UNIT','%s',0)"
                    .formatted(UUID.randomUUID(), fixture.supplierId(), fixture.variantId()));
            assertConstraint(connection, "insert into purchases(id,supplier_id,document_type,document_number,normalized_document_type,normalized_document_number,status,purchased_at,created_by) values ('%s','%s',' invoice ',' a-1 ','invoice','a-1','PENDING',current_date,'admin')"
                    .formatted(UUID.randomUUID(), fixture.supplierId()));
            assertConstraint(connection, "update purchases set status='UNKNOWN' where id='%s'".formatted(fixture.purchaseId()));
        }
    }

    @Test
    void keepsConfirmedEvidenceImmutableAndSourceAndIdempotencyUnique() throws Exception {
        try (Connection connection = POSTGRES.createConnection("")) {
            Fixture fixture = fixture(connection);
            UUID receiptId = UUID.randomUUID();
            UUID receiptLineId = UUID.randomUUID();
            UUID dispositionId = UUID.randomUUID();
            UUID movementId = UUID.randomUUID();
            execute(connection, "insert into purchase_receipts(id,purchase_id,kind,idempotency_key,request_hash,actor,correlation_id) values ('%s','%s','RECEIPT','key-1','%s','admin','corr-1')"
                    .formatted(receiptId, fixture.purchaseId(), "a".repeat(64)));
            execute(connection, "insert into purchase_receipt_lines(id,receipt_id,purchase_line_id) values ('%s','%s','%s')"
                    .formatted(receiptLineId, receiptId, fixture.purchaseLineId()));
            execute(connection, "insert into purchase_receipt_dispositions(id,receipt_line_id,type,quantity) values ('%s','%s','ACCEPTED_ORDERED',1.000000)"
                    .formatted(dispositionId, receiptLineId));
            execute(connection, "insert into stock_movements(id,source_type,source_id,purchase_id,receipt_id,target_type,target_id,quantity,conversion,canonical_delta,before_balance,after_balance,actor,correlation_id) values ('%s','RECEIPT','%s','%s','%s','VARIANT_UNIT','%s',1,1,1,5,6,'admin','corr-1')"
                    .formatted(movementId, dispositionId, fixture.purchaseId(), receiptId, fixture.variantId()));

            assertImmutable(connection, "update purchase_receipts set actor='other' where id='%s'".formatted(receiptId));
            assertImmutable(connection, "delete from purchase_receipt_lines where id='%s'".formatted(receiptLineId));
            assertImmutable(connection, "update purchase_receipt_dispositions set quantity=2 where id='%s'".formatted(dispositionId));
            assertImmutable(connection, "delete from stock_movements where id='%s'".formatted(movementId));
            assertConstraint(connection, "insert into purchase_receipts(id,purchase_id,kind,idempotency_key,request_hash,actor,correlation_id) values ('%s','%s','RECEIPT','key-1','%s','admin','corr-2')"
                    .formatted(UUID.randomUUID(), fixture.purchaseId(), "b".repeat(64)));
            assertConstraint(connection, "insert into stock_movements(id,source_type,source_id,purchase_id,receipt_id,target_type,target_id,quantity,conversion,canonical_delta,before_balance,after_balance,actor,correlation_id) values ('%s','RECEIPT','%s','%s','%s','VARIANT_UNIT','%s',1,1,1,6,7,'admin','corr-2')"
                    .formatted(UUID.randomUUID(), dispositionId, fixture.purchaseId(), receiptId, fixture.variantId()));
        }
    }

    @Test
    void enforcesConfirmedSnapshotProgressAndLifecycleInDatabase() throws Exception {
        try (Connection connection = POSTGRES.createConnection("")) {
            Fixture fixture = fixture(connection);
            assertInvariant(connection, "update purchases set status='RECEIVED' where id='%s'".formatted(fixture.purchaseId()));

            UUID receiptId = UUID.randomUUID();
            UUID receiptLineId = UUID.randomUUID();
            execute(connection, "insert into purchase_receipts(id,purchase_id,kind,idempotency_key,request_hash,actor,correlation_id) values ('%s','%s','RECEIPT','progress-key','%s','admin','corr-progress')"
                    .formatted(receiptId, fixture.purchaseId(), "c".repeat(64)));
            execute(connection, "insert into purchase_receipt_lines(id,receipt_id,purchase_line_id) values ('%s','%s','%s')"
                    .formatted(receiptLineId, receiptId, fixture.purchaseLineId()));
            execute(connection, "insert into purchase_receipt_dispositions(id,receipt_line_id,type,quantity) values ('%s','%s','ACCEPTED_ORDERED',1.000000)"
                    .formatted(UUID.randomUUID(), receiptLineId));

            assertInvariant(connection, "update purchase_lines set supplier_description='rewritten' where id='%s'".formatted(fixture.purchaseLineId()));
            assertInvariant(connection, "insert into purchase_receipt_dispositions(id,receipt_line_id,type,quantity) values ('%s','%s','ACCEPTED_ORDERED',2.000000)"
                    .formatted(UUID.randomUUID(), receiptLineId));
            assertInvariant(connection, "update purchases set status='RECEIVED' where id='%s'".formatted(fixture.purchaseId()));

            execute(connection, "insert into purchase_receipt_dispositions(id,receipt_line_id,type,quantity,note) values ('%s','%s','NOT_DELIVERABLE_FINAL',1.000000,'Unavailable')"
                    .formatted(UUID.randomUUID(), receiptLineId));
            execute(connection, "update purchases set status='RECEIVED' where id='%s'".formatted(fixture.purchaseId()));
            assertInvariant(connection, "update purchases set status='CANCELLED' where id='%s'".formatted(fixture.purchaseId()));
        }
    }

    @Test
    void enforcesNameMappingScopeDraftHashUniquenessAndConfirmedDraftImmutability() throws Exception {
        try (Connection connection = POSTGRES.createConnection("")) {
            Fixture fixture = fixture(connection);
            UUID mappingId = UUID.randomUUID();
            execute(connection, "insert into supplier_item_mappings(id,supplier_id,supplier_item_name,normalized_name,description,target_type,variant_id,default_conversion) values ('%s','%s','Cafe Premium','cafe premium','Cafe','VARIANT_UNIT','%s',1)"
                    .formatted(mappingId, fixture.supplierId(), fixture.variantId()));
            assertConstraint(connection, "insert into supplier_item_mappings(id,supplier_id,supplier_item_name,normalized_name,description,target_type,variant_id,default_conversion) values ('%s','%s','CAFE','cafe premium','Cafe','VARIANT_UNIT','%s',1)"
                    .formatted(UUID.randomUUID(), fixture.supplierId(), fixture.variantId()));

            UUID draftId = UUID.randomUUID();
            UUID lineId = UUID.randomUUID();
            execute(connection, "insert into purchase_drafts(id,supplier_id,status,source_type,source_sha256,created_by) values ('%s','%s','DRAFT','MANUAL','%s','admin')"
                    .formatted(draftId, fixture.supplierId(), "d".repeat(64)));
            assertConstraint(connection, "insert into purchase_drafts(id,supplier_id,status,source_type,source_sha256,created_by) values ('%s','%s','DRAFT','MANUAL','%s','admin')"
                    .formatted(UUID.randomUUID(), fixture.supplierId(), "d".repeat(64)));
            execute(connection, "insert into purchase_draft_lines(id,draft_id,source_row_number,source_product_name,normalized_product_name,source_quantity_value,quantity,unit,match_status,mapping_id,target_type,variant_id,conversion) values ('%s','%s',2,'Cafe','cafe','1',1,'UNIDAD','MATCHED','%s','VARIANT_UNIT','%s',1)"
                    .formatted(lineId, draftId, mappingId, fixture.variantId()));
            UUID receiptId = UUID.randomUUID();
            execute(connection, "insert into purchase_receipts(id,purchase_id,kind,idempotency_key,request_hash,actor,correlation_id) values ('%s','%s','RECEIPT','draft-fixture','%s','admin','corr-draft')"
                    .formatted(receiptId, fixture.purchaseId(), "f".repeat(64)));
            execute(connection, "update purchase_drafts set status='CONFIRMED',confirmed_purchase_id='%s',confirmed_receipt_id='%s',confirm_idempotency_key='key',confirm_request_hash='%s' where id='%s'"
                    .formatted(fixture.purchaseId(), receiptId, "e".repeat(64), draftId));
            assertImmutable(connection, "update purchase_drafts set purchase_date=current_date where id='%s'".formatted(draftId));
            assertImmutable(connection, "update purchase_draft_lines set quantity=2 where id='%s'".formatted(lineId));
        }
    }

    private static Fixture fixture(Connection connection) throws SQLException {
        UUID categoryId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        UUID supplierId = UUID.randomUUID();
        UUID mappingId = UUID.randomUUID();
        UUID purchaseId = UUID.randomUUID();
        UUID purchaseLineId = UUID.randomUUID();
        execute(connection, "insert into categories(id,name,slug,active,created_at,updated_at) values ('%s','Test','%s',true,now(),now())".formatted(categoryId, categoryId));
        execute(connection, "insert into products(id,name,slug,description,active,category_id,created_at,updated_at,product_type,inventory_policy,stock_reserved_base_grams) values ('%s','Test','%s','Test',true,'%s',now(),now(),'ENVASADO','PER_VARIANT',0)".formatted(productId, productId, categoryId));
        execute(connection, "insert into product_variants(id,product_id,sku,unit_type,price,stock_available,stock_reserved,active,created_at,updated_at) values ('%s','%s','%s','UNIT',1,5,0,true,now(),now())".formatted(variantId, productId, variantId));
        execute(connection, "insert into suppliers(id,name) values ('%s','Supplier')".formatted(supplierId));
        execute(connection, "insert into supplier_item_mappings(id,supplier_id,supplier_item_code,normalized_code,description,target_type,variant_id,default_conversion) values ('%s','%s',' Code ','code','Item','VARIANT_UNIT','%s',1.000000)".formatted(mappingId, supplierId, variantId));
        execute(connection, "insert into purchases(id,supplier_id,document_type,document_number,normalized_document_type,normalized_document_number,status,purchased_at,created_by) values ('%s','%s',' Invoice ',' A-1 ','invoice','a-1','PENDING',current_date,'admin')".formatted(purchaseId, supplierId));
        execute(connection, "insert into purchase_lines(id,purchase_id,mapping_id,supplier_item_code,supplier_description,target_type,variant_id,ordered_quantity,conversion) values ('%s','%s','%s','Code','Item','VARIANT_UNIT','%s',2.000000,1.000000)".formatted(purchaseLineId, purchaseId, mappingId, variantId));
        return new Fixture(productId, variantId, supplierId, purchaseId, purchaseLineId);
    }

    private static void assertConstraint(Connection connection, String sql) {
        PSQLException exception = assertThrows(PSQLException.class, () -> execute(connection, sql));
        assertEquals("23", exception.getSQLState().substring(0, 2));
    }

    private static void assertImmutable(Connection connection, String sql) {
        PSQLException exception = assertThrows(PSQLException.class, () -> execute(connection, sql));
        assertEquals("P0001", exception.getSQLState());
    }

    private static void assertInvariant(Connection connection, String sql) {
        PSQLException exception = assertThrows(PSQLException.class, () -> execute(connection, sql));
        assertEquals("P0001", exception.getSQLState());
    }

    private static void execute(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    private record Fixture(UUID productId, UUID variantId, UUID supplierId, UUID purchaseId, UUID purchaseLineId) { }
}
