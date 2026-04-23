alter table if exists products
    add column if not exists product_type varchar(20);

update products
set product_type = 'ENVASADO'
where product_type is null;

alter table if exists products
    alter column product_type set default 'ENVASADO';

alter table if exists products
    alter column product_type set not null;

alter table if exists products
    drop constraint if exists products_product_type_check;

alter table if exists products
    add constraint products_product_type_check
        check (product_type in ('GRANEL', 'ENVASADO', 'UNIDAD'));

alter table if exists products
    add column if not exists inventory_policy varchar(20);

update products
set inventory_policy = 'PER_VARIANT'
where inventory_policy is null;

alter table if exists products
    alter column inventory_policy set default 'PER_VARIANT';

alter table if exists products
    alter column inventory_policy set not null;

alter table if exists products
    drop constraint if exists products_inventory_policy_check;

alter table if exists products
    add constraint products_inventory_policy_check
        check (inventory_policy in ('BULK_WEIGHT', 'PER_VARIANT'));

alter table if exists products
    add column if not exists stock_base_grams integer;

alter table if exists products
    drop constraint if exists products_stock_base_grams_check;

alter table if exists products
    add constraint products_stock_base_grams_check
        check (stock_base_grams is null or stock_base_grams >= 0);

create index if not exists idx_products_product_type on products (product_type);
create index if not exists idx_products_inventory_policy on products (inventory_policy);
