alter table order_drafts
    add column if not exists fulfillment_method varchar(20) not null default 'PICKUP',
    add column if not exists delivery_address varchar(500),
    add column if not exists pickup_time varchar(120);
