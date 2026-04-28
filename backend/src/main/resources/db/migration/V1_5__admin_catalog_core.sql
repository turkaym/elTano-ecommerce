alter table if exists products
    add column if not exists deleted_at timestamp with time zone;

alter table if exists products
    add column if not exists deleted_by varchar(120);

alter table if exists products
    add column if not exists delete_reason varchar(400);

create index if not exists idx_products_deleted_at on products (deleted_at);

create table if not exists product_images (
    id uuid primary key,
    product_id uuid not null references products(id) on delete cascade,
    url varchar(600) not null,
    alt_text varchar(180),
    sort_order integer not null,
    is_primary boolean not null default false,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint uk_product_images_product_sort_order unique (product_id, sort_order),
    constraint chk_product_images_sort_order_non_negative check (sort_order >= 0)
);

create unique index if not exists idx_product_images_primary
    on product_images(product_id)
    where is_primary = true;

create index if not exists idx_product_images_product on product_images (product_id);
create index if not exists idx_product_images_sort_order on product_images (product_id, sort_order);
