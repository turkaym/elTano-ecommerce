package com.eltano.ecommerce.procurement.domain;

import java.math.BigDecimal;
import java.util.UUID;

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
import jakarta.persistence.Table;

@Entity
@Table(name = "purchase_receipt_dispositions")
public class PurchaseReceiptDisposition {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "receipt_line_id") private PurchaseReceiptLine receiptLine;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private DispositionType type;
    @Column(nullable = false, precision = 18, scale = 6) private BigDecimal quantity;
    @Column(length = 1000) private String note;
    private UUID correctedDispositionId;
    public UUID getId() { return id; }
    public PurchaseReceiptLine getReceiptLine() { return receiptLine; }
    public void setReceiptLine(PurchaseReceiptLine value) { receiptLine = value; }
    public DispositionType getType() { return type; }
    public void setType(DispositionType value) { type = value; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal value) { quantity = value; }
    public String getNote() { return note; }
    public void setNote(String value) { note = value; }
    public UUID getCorrectedDispositionId() { return correctedDispositionId; }
    public void setCorrectedDispositionId(UUID value) { correctedDispositionId = value; }
}
