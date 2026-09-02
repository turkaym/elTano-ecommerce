package com.eltano.ecommerce.catalog.pricing.domain;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "catalog_sale_price_previews")
public class CatalogSalePricePreview {
    @Id
    private UUID id;
    @Column(nullable = false, length = 20)
    private String status;
    @Column(nullable = false, length = 64)
    private String workbookSha256;
    @Column(nullable = false, length = 64)
    private String previewHash;
    @Column(nullable = false, columnDefinition = "text")
    private String snapshotJson;
    @Column(nullable = false, length = 120)
    private String createdBy;
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
    private Instant confirmedAt;
    @Column(length = 180)
    private String confirmIdempotencyKey;
    @Column(length = 64)
    private String confirmRequestHash;

    public UUID getId() { return id; }
    public void setId(UUID value) { id = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
    public String getWorkbookSha256() { return workbookSha256; }
    public void setWorkbookSha256(String value) { workbookSha256 = value; }
    public String getPreviewHash() { return previewHash; }
    public void setPreviewHash(String value) { previewHash = value; }
    public String getSnapshotJson() { return snapshotJson; }
    public void setSnapshotJson(String value) { snapshotJson = value; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String value) { createdBy = value; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getConfirmedAt() { return confirmedAt; }
    public void setConfirmedAt(Instant value) { confirmedAt = value; }
    public String getConfirmIdempotencyKey() { return confirmIdempotencyKey; }
    public void setConfirmIdempotencyKey(String value) { confirmIdempotencyKey = value; }
    public String getConfirmRequestHash() { return confirmRequestHash; }
    public void setConfirmRequestHash(String value) { confirmRequestHash = value; }
}
