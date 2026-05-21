create table if not exists admin_audit_events (
    id uuid primary key,
    actor varchar(120) not null,
    action varchar(20) not null,
    entity_type varchar(80) not null,
    entity_id varchar(120),
    outcome varchar(20) not null,
    correlation_id varchar(120) not null,
    before_after_summary varchar(2000),
    path varchar(240) not null,
    status_code integer not null,
    created_at timestamp with time zone not null
);

create index if not exists idx_admin_audit_events_created_at
    on admin_audit_events (created_at desc);

create index if not exists idx_admin_audit_events_entity
    on admin_audit_events (entity_type, entity_id);

create index if not exists idx_admin_audit_events_correlation
    on admin_audit_events (correlation_id);

create table if not exists admin_catalog_jobs (
    id uuid primary key,
    job_type varchar(40) not null,
    status varchar(30) not null,
    created_by varchar(120) not null,
    source_format varchar(20) not null,
    summary varchar(2000),
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    completed_at timestamp with time zone
);

create index if not exists idx_admin_catalog_jobs_status
    on admin_catalog_jobs (status);

create table if not exists admin_catalog_job_rows (
    id uuid primary key,
    job_id uuid not null references admin_catalog_jobs(id) on delete cascade,
    row_number integer not null,
    outcome varchar(20) not null,
    error_code varchar(80),
    error_message varchar(1000),
    payload_json varchar(4000),
    created_at timestamp with time zone not null,
    constraint uk_admin_catalog_job_rows unique (job_id, row_number)
);

create index if not exists idx_admin_catalog_job_rows_job
    on admin_catalog_job_rows (job_id);
