ALTER TABLE purchase_draft_lines ADD COLUMN target_label varchar(500);

UPDATE purchase_draft_lines line
SET target_label = CASE line.target_type
    WHEN 'BULK_GRAM' THEN (SELECT product.name || ' (a granel)' FROM products product WHERE product.id = line.product_id)
    WHEN 'VARIANT_UNIT' THEN (SELECT product.name || ' - ' || variant.sku FROM product_variants variant JOIN products product ON product.id = variant.product_id WHERE variant.id = line.variant_id)
END
FROM purchase_drafts draft
WHERE draft.id = line.draft_id AND draft.status = 'DRAFT'
    AND line.match_status = 'MATCHED' AND line.target_label IS NULL;
