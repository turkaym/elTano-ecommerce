create table if not exists order_drafts (
    id uuid primary key,
    reference varchar(40) not null unique,
    status varchar(20) not null,
    customer_name varchar(180) not null,
    phone varchar(60) not null,
    note varchar(1000),
    currency varchar(10) not null,
    subtotal numeric(12,2) not null,
    total numeric(12,2) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create table if not exists order_draft_lines (
    id uuid primary key,
    order_draft_id uuid not null references order_drafts(id),
    variant_id uuid not null references product_variants(id),
    product_name varchar(180) not null,
    unit_label varchar(80) not null,
    unit_price numeric(12,2) not null,
    quantity integer not null,
    line_total numeric(12,2) not null,
    created_at timestamp with time zone not null
);
