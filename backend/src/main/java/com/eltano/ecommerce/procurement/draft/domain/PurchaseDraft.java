package com.eltano.ecommerce.procurement.draft.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.eltano.ecommerce.procurement.domain.Supplier;

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
import jakarta.persistence.Version;

@Entity
@Table(name = "purchase_drafts")
public class PurchaseDraft {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "supplier_id") private Supplier supplier;
    @Version private long version;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private PurchaseDraftStatus status = PurchaseDraftStatus.DRAFT;
    private LocalDate purchaseDate;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private PurchaseDraftSourceType sourceType;
    @Column(length = 500) private String originalFilename;
    @Column(length = 180) private String sourceContentType;
    @Column(length = 180) private String sourceStorageKey;
    @Column(length = 64) private String sourceSha256;
    private Long sourceSize;
    @Column(length = 64) private String previewHash;
    private UUID confirmedPurchaseId;
    private UUID confirmedReceiptId;
    @Column(length = 180) private String confirmIdempotencyKey;
    @Column(length = 64) private String confirmRequestHash;
    @Column(nullable = false, length = 120) private String createdBy;
    @OneToMany(mappedBy = "draft", cascade = CascadeType.ALL, orphanRemoval = true) private List<PurchaseDraftLine> lines = new ArrayList<>();
    @CreationTimestamp @Column(nullable = false, updatable = false) private Instant createdAt;
    @UpdateTimestamp @Column(nullable = false) private Instant updatedAt;
    private Instant deletedAt;

    public UUID getId() { return id; }
    public Supplier getSupplier() { return supplier; }
    public void setSupplier(Supplier value) { supplier = value; }
    public long getVersion() { return version; }
    public PurchaseDraftStatus getStatus() { return status; }
    public void setStatus(PurchaseDraftStatus value) { status = value; }
    public LocalDate getPurchaseDate() { return purchaseDate; }
    public void setPurchaseDate(LocalDate value) { purchaseDate = value; }
    public PurchaseDraftSourceType getSourceType() { return sourceType; }
    public void setSourceType(PurchaseDraftSourceType value) { sourceType = value; }
    public String getOriginalFilename() { return originalFilename; }
    public void setOriginalFilename(String value) { originalFilename = value; }
    public String getSourceContentType() { return sourceContentType; }
    public void setSourceContentType(String value) { sourceContentType = value; }
    public String getSourceStorageKey() { return sourceStorageKey; }
    public void setSourceStorageKey(String value) { sourceStorageKey = value; }
    public String getSourceSha256() { return sourceSha256; }
    public void setSourceSha256(String value) { sourceSha256 = value; }
    public Long getSourceSize() { return sourceSize; }
    public void setSourceSize(Long value) { sourceSize = value; }
    public String getPreviewHash() { return previewHash; }
    public void setPreviewHash(String value) { previewHash = value; }
    public UUID getConfirmedPurchaseId() { return confirmedPurchaseId; }
    public void setConfirmedPurchaseId(UUID value) { confirmedPurchaseId = value; }
    public UUID getConfirmedReceiptId() { return confirmedReceiptId; }
    public void setConfirmedReceiptId(UUID value) { confirmedReceiptId = value; }
    public String getConfirmIdempotencyKey() { return confirmIdempotencyKey; }
    public void setConfirmIdempotencyKey(String value) { confirmIdempotencyKey = value; }
    public String getConfirmRequestHash() { return confirmRequestHash; }
    public void setConfirmRequestHash(String value) { confirmRequestHash = value; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String value) { createdBy = value; }
    public List<PurchaseDraftLine> getLines() { return lines; }
    public void addLine(PurchaseDraftLine line) { line.setDraft(this); lines.add(line); }
    public void removeLine(PurchaseDraftLine line) { lines.remove(line); }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getDeletedAt() { return deletedAt; }
    public void setDeletedAt(Instant value) { deletedAt = value; }
    public void invalidatePreview() { previewHash = null; }
}
