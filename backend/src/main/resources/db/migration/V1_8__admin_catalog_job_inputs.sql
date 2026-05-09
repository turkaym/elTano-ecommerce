create table if not exists admin_catalog_job_inputs (
    job_id uuid primary key,
    payload_text text not null,
    created_at timestamp with time zone not null default now(),
    constraint fk_admin_catalog_job_inputs_job
        foreign key (job_id)
        references admin_catalog_jobs (id)
        on delete cascade
);
