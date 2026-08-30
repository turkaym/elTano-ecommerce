package com.eltano.ecommerce.procurement.draft.domain;

import java.util.UUID;

import com.eltano.ecommerce.procurement.domain.Supplier;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "purchase_draft_import_keys", uniqueConstraints = @UniqueConstraint(columnNames = {"supplier_id", "idempotency_key"}))
public class PurchaseDraftImportKey {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "supplier_id") private Supplier supplier;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "draft_id") private PurchaseDraft draft;
    @Column(nullable = false, length = 180) private String idempotencyKey;
    @Column(nullable = false, length = 64) private String sourceSha256;

    protected PurchaseDraftImportKey() { }
    public PurchaseDraftImportKey(Supplier supplier, PurchaseDraft draft, String key, String hash) {
        this.supplier = supplier; this.draft = draft; this.idempotencyKey = key; this.sourceSha256 = hash;
    }
    public PurchaseDraft getDraft() { return draft; }
    public String getSourceSha256() { return sourceSha256; }
}
