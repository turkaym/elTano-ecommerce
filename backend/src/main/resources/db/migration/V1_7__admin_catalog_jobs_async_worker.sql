alter table admin_catalog_jobs
    add column if not exists attempt_count integer not null default 0,
    add column if not exists max_attempts integer not null default 3,
    add column if not exists leased_until timestamp with time zone,
    add column if not exists leased_by varchar(120),
    add column if not exists last_error varchar(2000),
    add column if not exists started_at timestamp with time zone,
    add column if not exists next_attempt_at timestamp with time zone;

create index if not exists idx_admin_catalog_jobs_claimable
    on admin_catalog_jobs (status, next_attempt_at, leased_until, created_at);

create index if not exists idx_admin_catalog_jobs_lease_owner
    on admin_catalog_jobs (leased_by, leased_until);
