package com.eltano.ecommerce.procurement.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

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
@Table(name = "purchases", uniqueConstraints = @UniqueConstraint(name = "uk_purchase_document",
        columnNames = {"supplier_id", "normalized_document_type", "normalized_document_number"}))
public class Purchase {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "supplier_id") private Supplier supplier;
    @Column(nullable = false, length = 80) private String documentType;
    @Column(nullable = false, length = 120) private String documentNumber;
    @Column(nullable = false, length = 80) private String normalizedDocumentType;
    @Column(nullable = false, length = 120) private String normalizedDocumentNumber;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private PurchaseStatus status = PurchaseStatus.PENDING;
    @Column(nullable = false) private LocalDate purchasedAt;
    @Column(nullable = false, length = 120) private String createdBy;
    @OneToMany(mappedBy = "purchase", cascade = CascadeType.ALL, orphanRemoval = true) private List<PurchaseLine> lines = new ArrayList<>();
    @CreationTimestamp @Column(nullable = false, updatable = false) private Instant createdAt;
    @UpdateTimestamp @Column(nullable = false) private Instant updatedAt;
    public UUID getId() { return id; }
    public Supplier getSupplier() { return supplier; }
    public void setSupplier(Supplier value) { supplier = value; }
    public String getDocumentType() { return documentType; }
    public void setDocumentType(String value) { documentType = value; }
    public String getDocumentNumber() { return documentNumber; }
    public void setDocumentNumber(String value) { documentNumber = value; }
    public String getNormalizedDocumentType() { return normalizedDocumentType; }
    public void setNormalizedDocumentType(String value) { normalizedDocumentType = value; }
    public String getNormalizedDocumentNumber() { return normalizedDocumentNumber; }
    public void setNormalizedDocumentNumber(String value) { normalizedDocumentNumber = value; }
    public PurchaseStatus getStatus() { return status; }
    public void setStatus(PurchaseStatus value) { status = value; }
    public LocalDate getPurchasedAt() { return purchasedAt; }
    public void setPurchasedAt(LocalDate value) { purchasedAt = value; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String value) { createdBy = value; }
    public List<PurchaseLine> getLines() { return lines; }
    public void replaceLines(List<PurchaseLine> values) { lines.clear(); values.forEach(this::addLine); }
    public void addLine(PurchaseLine value) { value.setPurchase(this); lines.add(value); }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
