alter table order_drafts drop constraint if exists chk_order_drafts_status;

alter table order_drafts add constraint chk_order_drafts_status
    check (status in ('DRAFT', 'PAYMENT_PENDING', 'PAID', 'PREPARING', 'READY', 'DELIVERED', 'FAILED', 'CANCELLED', 'EXPIRED'));
