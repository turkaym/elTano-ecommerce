alter table if exists products
    add column if not exists stock_reserved_base_grams integer;

alter table if exists products
    alter column stock_reserved_base_grams set default 0;

update products
set stock_reserved_base_grams = 0
where stock_reserved_base_grams is null;

alter table if exists products
    alter column stock_reserved_base_grams set not null;

alter table if exists products
    drop constraint if exists products_stock_reserved_base_grams_check;

alter table if exists products
    add constraint products_stock_reserved_base_grams_check
        check (stock_reserved_base_grams >= 0);
