CREATE TABLE suppliers (
    id uuid PRIMARY KEY,
    name varchar(180) NOT NULL,
    tax_identity varchar(120),
    active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE supplier_item_mappings (
    id uuid PRIMARY KEY,
    supplier_id uuid NOT NULL REFERENCES suppliers(id),
    supplier_item_code varchar(180) NOT NULL,
    normalized_code varchar(180) NOT NULL CHECK (normalized_code = lower(btrim(supplier_item_code))),
    description varchar(500) NOT NULL,
    target_type varchar(20) NOT NULL,
    product_id uuid REFERENCES products(id),
    variant_id uuid REFERENCES product_variants(id),
    default_conversion numeric(18,6) NOT NULL CHECK (default_conversion > 0),
    active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (supplier_id, normalized_code),
    CHECK ((target_type = 'VARIANT_UNIT' AND variant_id IS NOT NULL AND product_id IS NULL)
        OR (target_type = 'BULK_GRAM' AND product_id IS NOT NULL AND variant_id IS NULL))
);

CREATE TABLE purchases (
    id uuid PRIMARY KEY,
    supplier_id uuid NOT NULL REFERENCES suppliers(id),
    document_type varchar(80) NOT NULL,
    document_number varchar(120) NOT NULL,
    normalized_document_type varchar(80) NOT NULL CHECK (normalized_document_type = lower(btrim(document_type))),
    normalized_document_number varchar(120) NOT NULL CHECK (normalized_document_number = lower(btrim(document_number))),
    status varchar(20) NOT NULL DEFAULT 'PENDING',
    purchased_at date NOT NULL,
    created_by varchar(120) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT purchase_status_check CHECK (status IN ('PENDING', 'RECEIVED', 'CANCELLED')),
    UNIQUE (supplier_id, normalized_document_type, normalized_document_number)
);
CREATE INDEX idx_purchases_supplier_status_date ON purchases(supplier_id, status, purchased_at DESC);

CREATE TABLE purchase_lines (
    id uuid PRIMARY KEY,
    purchase_id uuid NOT NULL REFERENCES purchases(id) ON DELETE CASCADE,
    mapping_id uuid NOT NULL REFERENCES supplier_item_mappings(id),
    supplier_item_code varchar(180) NOT NULL,
    supplier_description varchar(500) NOT NULL,
    target_type varchar(20) NOT NULL,
    product_id uuid REFERENCES products(id),
    variant_id uuid REFERENCES product_variants(id),
    ordered_quantity numeric(18,6) NOT NULL CHECK (ordered_quantity > 0),
    conversion numeric(18,6) NOT NULL CHECK (conversion > 0),
    CHECK ((target_type = 'VARIANT_UNIT' AND variant_id IS NOT NULL AND product_id IS NULL)
        OR (target_type = 'BULK_GRAM' AND product_id IS NOT NULL AND variant_id IS NULL))
);

CREATE TABLE purchase_receipts (
    id uuid PRIMARY KEY,
    purchase_id uuid NOT NULL REFERENCES purchases(id),
    kind varchar(20) NOT NULL CHECK (kind IN ('RECEIPT', 'CORRECTION', 'CANCELLATION')),
    idempotency_key varchar(180) NOT NULL,
    request_hash varchar(64) NOT NULL,
    note varchar(1000),
    actor varchar(120) NOT NULL,
    correlation_id varchar(120) NOT NULL,
    confirmed_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (purchase_id, kind, idempotency_key),
    CHECK (kind <> 'CORRECTION' OR length(btrim(note)) > 0)
);

CREATE TABLE purchase_receipt_lines (
    id uuid PRIMARY KEY,
    receipt_id uuid NOT NULL REFERENCES purchase_receipts(id),
    purchase_line_id uuid NOT NULL REFERENCES purchase_lines(id),
    UNIQUE (receipt_id, purchase_line_id)
);

CREATE TABLE purchase_receipt_dispositions (
    id uuid PRIMARY KEY,
    receipt_line_id uuid NOT NULL REFERENCES purchase_receipt_lines(id),
    type varchar(30) NOT NULL CHECK (type IN ('ACCEPTED_ORDERED','ACCEPTED_EXCESS','TEMP_MISSING','REJECTED_FINAL','NOT_DELIVERABLE_FINAL')),
    quantity numeric(18,6) NOT NULL CHECK (quantity > 0),
    note varchar(1000),
    corrected_disposition_id uuid REFERENCES purchase_receipt_dispositions(id),
    CHECK (type <> 'ACCEPTED_EXCESS' OR length(btrim(note)) > 0)
);

CREATE TABLE stock_movements (
    id uuid PRIMARY KEY,
    source_type varchar(30) NOT NULL,
    source_id uuid NOT NULL,
    purchase_id uuid NOT NULL REFERENCES purchases(id),
    receipt_id uuid NOT NULL REFERENCES purchase_receipts(id),
    target_type varchar(20) NOT NULL,
    target_id uuid NOT NULL,
    quantity numeric(18,6) NOT NULL,
    conversion numeric(18,6) NOT NULL CHECK (conversion > 0),
    canonical_delta integer NOT NULL CHECK (canonical_delta <> 0),
    before_balance integer NOT NULL CHECK (before_balance >= 0),
    after_balance integer NOT NULL CHECK (after_balance >= 0),
    actor varchar(120) NOT NULL,
    correlation_id varchar(120) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (source_type, source_id)
);
CREATE INDEX idx_stock_movements_purchase ON stock_movements(purchase_id, created_at);
CREATE INDEX idx_stock_movements_target ON stock_movements(target_type, target_id, created_at);

CREATE FUNCTION validate_procurement_disposition_progress() RETURNS trigger AS $$
DECLARE
    line_id uuid;
    ordered numeric(18,6);
    finalized numeric(18,6);
BEGIN
    IF NEW.type IN ('TEMP_MISSING', 'ACCEPTED_EXCESS') THEN
        RETURN NEW;
    END IF;
    SELECT prl.purchase_line_id, pl.ordered_quantity
      INTO line_id, ordered
      FROM purchase_receipt_lines prl
      JOIN purchase_lines pl ON pl.id = prl.purchase_line_id
     WHERE prl.id = NEW.receipt_line_id;
    SELECT COALESCE(sum(prd.quantity), 0)
      INTO finalized
      FROM purchase_receipt_dispositions prd
      JOIN purchase_receipt_lines prl ON prl.id = prd.receipt_line_id
     WHERE prl.purchase_line_id = line_id
       AND prd.type IN ('ACCEPTED_ORDERED', 'REJECTED_FINAL', 'NOT_DELIVERABLE_FINAL');
    IF finalized + NEW.quantity > ordered THEN
        RAISE EXCEPTION 'Final disposition exceeds ordered quantity';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER valid_procurement_disposition_progress
BEFORE INSERT ON purchase_receipt_dispositions
FOR EACH ROW EXECUTE FUNCTION validate_procurement_disposition_progress();

CREATE FUNCTION protect_purchase_line_snapshot() RETURNS trigger AS $$
DECLARE
    target_purchase_id uuid;
    target_line_id uuid;
BEGIN
    target_purchase_id := CASE WHEN TG_OP = 'DELETE' THEN OLD.purchase_id ELSE NEW.purchase_id END;
    target_line_id := CASE WHEN TG_OP = 'INSERT' THEN NULL ELSE OLD.id END;
    IF EXISTS (
        SELECT 1 FROM purchase_receipts pr
         WHERE pr.purchase_id = target_purchase_id
    ) OR (target_line_id IS NOT NULL AND EXISTS (
        SELECT 1 FROM purchase_receipt_lines prl
         WHERE prl.purchase_line_id = target_line_id
    )) THEN
        RAISE EXCEPTION 'Confirmed purchase line snapshots are immutable';
    END IF;
    RETURN CASE WHEN TG_OP = 'DELETE' THEN OLD ELSE NEW END;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER immutable_confirmed_purchase_lines
BEFORE INSERT OR UPDATE OR DELETE ON purchase_lines
FOR EACH ROW EXECUTE FUNCTION protect_purchase_line_snapshot();

CREATE FUNCTION validate_purchase_status_transition() RETURNS trigger AS $$
BEGIN
    IF OLD.status = 'CANCELLED' AND NEW.status <> 'CANCELLED' THEN
        RAISE EXCEPTION 'Cancelled purchases cannot transition';
    END IF;
    IF OLD.status = 'RECEIVED' AND NEW.status = 'PENDING' THEN
        RAISE EXCEPTION 'Received purchases cannot return to pending';
    END IF;
    IF NEW.status = 'RECEIVED' AND EXISTS (
        SELECT 1
          FROM purchase_lines pl
         WHERE pl.purchase_id = NEW.id
           AND COALESCE((
               SELECT sum(prd.quantity)
                 FROM purchase_receipt_dispositions prd
                 JOIN purchase_receipt_lines prl ON prl.id = prd.receipt_line_id
                WHERE prl.purchase_line_id = pl.id
                  AND prd.type IN ('ACCEPTED_ORDERED', 'REJECTED_FINAL', 'NOT_DELIVERABLE_FINAL')
           ), 0) <> pl.ordered_quantity
    ) THEN
        RAISE EXCEPTION 'Purchase cannot be received with outstanding quantities';
    END IF;
    IF NEW.status = 'CANCELLED' AND NOT EXISTS (
        SELECT 1 FROM purchase_receipts pr
         WHERE pr.purchase_id = NEW.id AND pr.kind = 'CANCELLATION'
    ) THEN
        RAISE EXCEPTION 'Purchase cancellation requires cancellation evidence';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER valid_purchase_status_transition
BEFORE UPDATE OF status ON purchases
FOR EACH ROW EXECUTE FUNCTION validate_purchase_status_transition();

CREATE FUNCTION reject_confirmed_procurement_evidence_update() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'Confirmed procurement evidence is immutable';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER immutable_purchase_receipts BEFORE UPDATE OR DELETE ON purchase_receipts
FOR EACH ROW EXECUTE FUNCTION reject_confirmed_procurement_evidence_update();
CREATE TRIGGER immutable_purchase_receipt_lines BEFORE UPDATE OR DELETE ON purchase_receipt_lines
FOR EACH ROW EXECUTE FUNCTION reject_confirmed_procurement_evidence_update();
CREATE TRIGGER immutable_purchase_receipt_dispositions BEFORE UPDATE OR DELETE ON purchase_receipt_dispositions
FOR EACH ROW EXECUTE FUNCTION reject_confirmed_procurement_evidence_update();
CREATE TRIGGER immutable_stock_movements BEFORE UPDATE OR DELETE ON stock_movements
FOR EACH ROW EXECUTE FUNCTION reject_confirmed_procurement_evidence_update();
