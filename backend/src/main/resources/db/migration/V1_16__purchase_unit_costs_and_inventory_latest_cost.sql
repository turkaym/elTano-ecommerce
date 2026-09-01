ALTER TABLE purchase_draft_lines
    ADD COLUMN source_unit_price_value varchar(120),
    ADD COLUMN unit_price numeric(19,2),
    ADD COLUMN line_total numeric(19,2),
    ADD COLUMN pricing_unit varchar(20),
    ADD COLUMN currency varchar(3),
    ADD CONSTRAINT purchase_draft_line_cost_check CHECK (
        (unit_price IS NULL AND line_total IS NULL AND pricing_unit IS NULL AND currency IS NULL)
        OR (unit_price IS NOT NULL AND line_total IS NOT NULL AND pricing_unit IS NOT NULL AND currency IS NOT NULL
            AND unit_price > 0 AND line_total > 0 AND pricing_unit IN ('KG', 'UNIDAD') AND currency = 'ARS')
    );

ALTER TABLE purchase_lines
    ADD COLUMN unit_price numeric(19,2),
    ADD COLUMN line_total numeric(19,2),
    ADD COLUMN pricing_unit varchar(20),
    ADD COLUMN currency varchar(3),
    ADD CONSTRAINT purchase_line_cost_check CHECK (
        (unit_price IS NULL AND line_total IS NULL AND pricing_unit IS NULL AND currency IS NULL)
        OR (unit_price IS NOT NULL AND line_total IS NOT NULL AND pricing_unit IS NOT NULL AND currency IS NOT NULL
            AND unit_price > 0 AND line_total > 0 AND pricing_unit IN ('KG', 'UNIDAD') AND currency = 'ARS')
    );

ALTER TABLE products
    ADD COLUMN latest_unit_cost numeric(19,2),
    ADD COLUMN latest_cost_unit varchar(20),
    ADD COLUMN latest_cost_at timestamptz,
    ADD COLUMN latest_cost_purchase_line_id uuid REFERENCES purchase_lines(id),
    ADD COLUMN latest_cost_receipt_id uuid REFERENCES purchase_receipts(id),
    ADD CONSTRAINT product_latest_cost_check CHECK (
        (latest_unit_cost IS NULL AND latest_cost_unit IS NULL AND latest_cost_at IS NULL
            AND latest_cost_purchase_line_id IS NULL AND latest_cost_receipt_id IS NULL)
        OR (latest_unit_cost IS NOT NULL AND latest_cost_unit IS NOT NULL AND latest_unit_cost > 0 AND latest_cost_unit = 'KG' AND latest_cost_at IS NOT NULL
            AND latest_cost_purchase_line_id IS NOT NULL AND latest_cost_receipt_id IS NOT NULL)
    );

ALTER TABLE product_variants
    ADD COLUMN latest_unit_cost numeric(19,2),
    ADD COLUMN latest_cost_unit varchar(20),
    ADD COLUMN latest_cost_at timestamptz,
    ADD COLUMN latest_cost_purchase_line_id uuid REFERENCES purchase_lines(id),
    ADD COLUMN latest_cost_receipt_id uuid REFERENCES purchase_receipts(id),
    ADD CONSTRAINT product_variant_latest_cost_check CHECK (
        (latest_unit_cost IS NULL AND latest_cost_unit IS NULL AND latest_cost_at IS NULL
            AND latest_cost_purchase_line_id IS NULL AND latest_cost_receipt_id IS NULL)
        OR (latest_unit_cost IS NOT NULL AND latest_cost_unit IS NOT NULL AND latest_unit_cost > 0 AND latest_cost_unit = 'UNIDAD' AND latest_cost_at IS NOT NULL
            AND latest_cost_purchase_line_id IS NOT NULL AND latest_cost_receipt_id IS NOT NULL)
    );
