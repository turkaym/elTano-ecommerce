create table if not exists categories (
    id uuid primary key,
    name varchar(120) not null,
    slug varchar(120) not null,
    active boolean not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint uk_categories_slug unique (slug)
);

create table if not exists products (
    id uuid primary key,
    name varchar(180) not null,
    slug varchar(180) not null,
    description varchar(2000) not null,
    active boolean not null,
    category_id uuid not null references categories(id),
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint uk_products_slug unique (slug)
);

create table if not exists product_variants (
    id uuid primary key,
    product_id uuid not null references products(id),
    sku varchar(120) not null,
    unit_type varchar(20) not null,
    weight_grams integer,
    unit_label varchar(40),
    price numeric(12,2) not null,
    stock_available integer not null,
    stock_reserved integer not null,
    active boolean not null,
    attributes_json varchar(4000),
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    constraint uk_product_variants_sku unique (sku)
);

create index if not exists idx_products_category on products (category_id);
create index if not exists idx_product_variants_product on product_variants (product_id);
