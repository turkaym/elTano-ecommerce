ALTER TABLE supplier_item_mappings
    ALTER COLUMN supplier_item_code DROP NOT NULL,
    ALTER COLUMN normalized_code DROP NOT NULL,
    ADD COLUMN supplier_item_name varchar(500),
    ADD COLUMN normalized_name varchar(500);

ALTER TABLE supplier_item_mappings
    DROP CONSTRAINT supplier_item_mappings_supplier_id_normalized_code_key;

ALTER TABLE supplier_item_mappings
    ADD CONSTRAINT supplier_mapping_identifier_check CHECK (
        (supplier_item_code IS NULL) = (normalized_code IS NULL)
        AND (supplier_item_name IS NULL) = (normalized_name IS NULL)
        AND (normalized_code IS NOT NULL OR normalized_name IS NOT NULL)
    );

CREATE UNIQUE INDEX uk_supplier_mapping_code
    ON supplier_item_mappings(supplier_id, normalized_code)
    WHERE normalized_code IS NOT NULL;
CREATE UNIQUE INDEX uk_supplier_mapping_name
    ON supplier_item_mappings(supplier_id, normalized_name)
    WHERE normalized_name IS NOT NULL;

ALTER TABLE purchase_lines ALTER COLUMN mapping_id DROP NOT NULL;

CREATE TABLE purchase_drafts (
    id uuid PRIMARY KEY,
    supplier_id uuid NOT NULL REFERENCES suppliers(id),
    version bigint NOT NULL DEFAULT 0,
    status varchar(20) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'CONFIRMED', 'DELETED')),
    purchase_date date,
    source_type varchar(20) NOT NULL CHECK (source_type IN ('MANUAL', 'XLSX')),
    original_filename varchar(500),
    source_content_type varchar(180),
    source_storage_key varchar(180),
    source_sha256 varchar(64),
    source_size bigint,
    preview_hash varchar(64),
    confirmed_purchase_id uuid REFERENCES purchases(id),
    confirmed_receipt_id uuid REFERENCES purchase_receipts(id),
    confirm_idempotency_key varchar(180),
    confirm_request_hash varchar(64),
    created_by varchar(120) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted_at timestamptz,
    CHECK ((source_type = 'XLSX' AND source_storage_key IS NOT NULL AND source_sha256 IS NOT NULL
            AND original_filename IS NOT NULL AND source_size IS NOT NULL)
        OR source_type = 'MANUAL'),
    CHECK ((status = 'CONFIRMED' AND confirmed_purchase_id IS NOT NULL AND confirmed_receipt_id IS NOT NULL
            AND confirm_idempotency_key IS NOT NULL AND confirm_request_hash IS NOT NULL)
        OR status <> 'CONFIRMED')
);

CREATE UNIQUE INDEX uk_purchase_draft_supplier_source_hash
    ON purchase_drafts(supplier_id, source_sha256)
    WHERE source_sha256 IS NOT NULL AND status <> 'DELETED';
CREATE INDEX idx_purchase_drafts_status_updated ON purchase_drafts(status, updated_at DESC);
CREATE INDEX idx_purchase_drafts_supplier_updated ON purchase_drafts(supplier_id, updated_at DESC);

CREATE TABLE purchase_draft_import_keys (
    id uuid PRIMARY KEY,
    supplier_id uuid NOT NULL REFERENCES suppliers(id),
    idempotency_key varchar(180) NOT NULL,
    draft_id uuid NOT NULL REFERENCES purchase_drafts(id) ON DELETE CASCADE,
    source_sha256 varchar(64) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (supplier_id, idempotency_key)
);

CREATE TABLE purchase_draft_lines (
    id uuid PRIMARY KEY,
    draft_id uuid NOT NULL REFERENCES purchase_drafts(id) ON DELETE CASCADE,
    source_row_number integer,
    source_date_value varchar(120),
    source_date date,
    source_product_name varchar(500) NOT NULL,
    normalized_product_name varchar(500) NOT NULL,
    source_quantity_value varchar(120) NOT NULL,
    quantity numeric(18,6),
    unit varchar(20) CHECK (unit IN ('KG', 'UNIDAD')),
    validation_errors text,
    match_status varchar(20) NOT NULL CHECK (match_status IN ('UNRESOLVED', 'MATCHED', 'INVALID')),
    mapping_id uuid REFERENCES supplier_item_mappings(id),
    target_type varchar(20),
    product_id uuid REFERENCES products(id),
    variant_id uuid REFERENCES product_variants(id),
    conversion numeric(18,6),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (draft_id, source_row_number),
    CHECK ((match_status = 'MATCHED' AND conversion > 0
            AND ((target_type = 'VARIANT_UNIT' AND variant_id IS NOT NULL AND product_id IS NULL)
              OR (target_type = 'BULK_GRAM' AND product_id IS NOT NULL AND variant_id IS NULL)))
        OR match_status <> 'MATCHED')
);
CREATE INDEX idx_purchase_draft_lines_draft ON purchase_draft_lines(draft_id, source_row_number);

CREATE FUNCTION protect_confirmed_purchase_draft() RETURNS trigger AS $$
BEGIN
    IF OLD.status = 'CONFIRMED' THEN
        RAISE EXCEPTION 'Confirmed purchase drafts are immutable';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER immutable_confirmed_purchase_drafts
BEFORE UPDATE OR DELETE ON purchase_drafts
FOR EACH ROW EXECUTE FUNCTION protect_confirmed_purchase_draft();

CREATE FUNCTION protect_confirmed_purchase_draft_line() RETURNS trigger AS $$
DECLARE
    parent_id uuid;
BEGIN
    parent_id := CASE WHEN TG_OP = 'DELETE' THEN OLD.draft_id ELSE NEW.draft_id END;
    IF EXISTS (SELECT 1 FROM purchase_drafts WHERE id = parent_id AND status = 'CONFIRMED') THEN
        RAISE EXCEPTION 'Confirmed purchase draft lines are immutable';
    END IF;
    RETURN CASE WHEN TG_OP = 'DELETE' THEN OLD ELSE NEW END;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER immutable_confirmed_purchase_draft_lines
BEFORE INSERT OR UPDATE OR DELETE ON purchase_draft_lines
FOR EACH ROW EXECUTE FUNCTION protect_confirmed_purchase_draft_line();
