package com.eltano.ecommerce.procurement.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "purchase_receipts", uniqueConstraints = @UniqueConstraint(name = "uk_purchase_receipt_key",
        columnNames = {"purchase_id", "kind", "idempotency_key"}))
public class PurchaseReceipt {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "purchase_id") private Purchase purchase;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private ReceiptKind kind;
    @Column(nullable = false, length = 180) private String idempotencyKey;
    @Column(nullable = false, length = 64) private String requestHash;
    @Column(length = 1000) private String note;
    @Column(nullable = false, length = 120) private String actor;
    @Column(nullable = false, length = 120) private String correlationId;
    @CreationTimestamp @Column(nullable = false, updatable = false) private Instant confirmedAt;
    @OneToMany(mappedBy = "receipt", cascade = CascadeType.ALL) private List<PurchaseReceiptLine> lines = new ArrayList<>();
    public UUID getId() { return id; }
    public Purchase getPurchase() { return purchase; }
    public void setPurchase(Purchase value) { purchase = value; }
    public ReceiptKind getKind() { return kind; }
    public void setKind(ReceiptKind value) { kind = value; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String value) { idempotencyKey = value; }
    public String getRequestHash() { return requestHash; }
    public void setRequestHash(String value) { requestHash = value; }
    public String getNote() { return note; }
    public void setNote(String value) { note = value; }
    public String getActor() { return actor; }
    public void setActor(String value) { actor = value; }
    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String value) { correlationId = value; }
    public Instant getConfirmedAt() { return confirmedAt; }
    public List<PurchaseReceiptLine> getLines() { return lines; }
    public void addLine(PurchaseReceiptLine line) { line.setReceipt(this); lines.add(line); }
}
