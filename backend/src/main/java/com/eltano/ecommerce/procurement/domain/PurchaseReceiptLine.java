package com.eltano.ecommerce.procurement.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

@Entity
@Table(name = "purchase_receipt_lines")
public class PurchaseReceiptLine {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "receipt_id") private PurchaseReceipt receipt;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "purchase_line_id") private PurchaseLine purchaseLine;
    @OneToMany(mappedBy = "receiptLine", cascade = CascadeType.ALL) private List<PurchaseReceiptDisposition> dispositions = new ArrayList<>();
    public UUID getId() { return id; }
    public PurchaseReceipt getReceipt() { return receipt; }
    public void setReceipt(PurchaseReceipt value) { receipt = value; }
    public PurchaseLine getPurchaseLine() { return purchaseLine; }
    public void setPurchaseLine(PurchaseLine value) { purchaseLine = value; }
    public List<PurchaseReceiptDisposition> getDispositions() { return dispositions; }
    public void addDisposition(PurchaseReceiptDisposition value) { value.setReceiptLine(this); dispositions.add(value); }
}
