alter table if exists order_drafts
    drop constraint if exists order_drafts_status_check;

alter table if exists order_drafts
    add constraint order_drafts_status_check
        check (status in ('DRAFT', 'PAYMENT_PENDING', 'PAID', 'FAILED', 'CANCELLED', 'EXPIRED'));
