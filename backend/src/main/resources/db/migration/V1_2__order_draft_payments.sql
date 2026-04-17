alter table if exists order_drafts
    add column if not exists payment_provider varchar(40),
    add column if not exists payment_preference_id varchar(120),
    add column if not exists payment_external_id varchar(120),
    add column if not exists payment_status_detail varchar(120),
    add column if not exists payment_updated_at timestamp with time zone,
    add column if not exists stock_released_at timestamp with time zone;

create table if not exists payment_webhook_events (
    id uuid primary key,
    provider varchar(40) not null,
    provider_event_id varchar(120) not null,
    payment_external_id varchar(120),
    payload_hash varchar(128),
    outcome varchar(80) not null,
    processed_at timestamp with time zone not null,
    created_at timestamp with time zone not null,
    unique (provider_event_id)
);
