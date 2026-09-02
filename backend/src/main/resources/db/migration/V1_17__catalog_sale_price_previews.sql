CREATE TABLE catalog_sale_price_previews (
    id uuid PRIMARY KEY,
    status varchar(20) NOT NULL CHECK (status IN ('READY', 'CONFIRMED')),
    workbook_sha256 varchar(64) NOT NULL,
    preview_hash varchar(64) NOT NULL,
    snapshot_json text NOT NULL,
    created_by varchar(120) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    confirmed_at timestamptz,
    confirm_idempotency_key varchar(180),
    confirm_request_hash varchar(64),
    CHECK ((status = 'CONFIRMED' AND confirmed_at IS NOT NULL
            AND confirm_idempotency_key IS NOT NULL AND confirm_request_hash IS NOT NULL)
        OR status = 'READY')
);

CREATE INDEX idx_catalog_sale_price_previews_created_at
    ON catalog_sale_price_previews(created_at DESC);

CREATE UNIQUE INDEX uk_catalog_sale_price_confirm_key
    ON catalog_sale_price_previews(confirm_idempotency_key)
    WHERE confirm_idempotency_key IS NOT NULL;
