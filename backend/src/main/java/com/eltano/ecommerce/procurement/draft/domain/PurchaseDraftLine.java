package com.eltano.ecommerce.procurement.draft.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.eltano.ecommerce.procurement.domain.InventoryTargetType;

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
@Table(name = "purchase_draft_lines")
public class PurchaseDraftLine {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "draft_id") private PurchaseDraft draft;
    private Integer sourceRowNumber;
    @Column(length = 120) private String sourceDateValue;
    private LocalDate sourceDate;
    @Column(nullable = false, length = 500) private String sourceProductName;
    @Column(nullable = false, length = 500) private String normalizedProductName;
    @Column(nullable = false, length = 120) private String sourceQuantityValue;
    @Column(precision = 18, scale = 6) private BigDecimal quantity;
    @Enumerated(EnumType.STRING) @Column(length = 20) private PurchaseDraftUnit unit;
    @Column(columnDefinition = "text") private String validationErrors;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private PurchaseDraftMatchStatus matchStatus;
    private UUID mappingId;
    @Enumerated(EnumType.STRING) @Column(length = 20) private InventoryTargetType targetType;
    private UUID productId;
    private UUID variantId;
    @Column(precision = 18, scale = 6) private BigDecimal conversion;
    @CreationTimestamp @Column(nullable = false, updatable = false) private Instant createdAt;
    @UpdateTimestamp @Column(nullable = false) private Instant updatedAt;

    public UUID getId() { return id; }
    public PurchaseDraft getDraft() { return draft; }
    public void setDraft(PurchaseDraft value) { draft = value; }
    public Integer getSourceRowNumber() { return sourceRowNumber; }
    public void setSourceRowNumber(Integer value) { sourceRowNumber = value; }
    public String getSourceDateValue() { return sourceDateValue; }
    public void setSourceDateValue(String value) { sourceDateValue = value; }
    public LocalDate getSourceDate() { return sourceDate; }
    public void setSourceDate(LocalDate value) { sourceDate = value; }
    public String getSourceProductName() { return sourceProductName; }
    public void setSourceProductName(String value) { sourceProductName = value; }
    public String getNormalizedProductName() { return normalizedProductName; }
    public void setNormalizedProductName(String value) { normalizedProductName = value; }
    public String getSourceQuantityValue() { return sourceQuantityValue; }
    public void setSourceQuantityValue(String value) { sourceQuantityValue = value; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal value) { quantity = value; }
    public PurchaseDraftUnit getUnit() { return unit; }
    public void setUnit(PurchaseDraftUnit value) { unit = value; }
    public String getValidationErrors() { return validationErrors; }
    public void setValidationErrors(String value) { validationErrors = value; }
    public PurchaseDraftMatchStatus getMatchStatus() { return matchStatus; }
    public void setMatchStatus(PurchaseDraftMatchStatus value) { matchStatus = value; }
    public UUID getMappingId() { return mappingId; }
    public void setMappingId(UUID value) { mappingId = value; }
    public InventoryTargetType getTargetType() { return targetType; }
    public void setTargetType(InventoryTargetType value) { targetType = value; }
    public UUID getProductId() { return productId; }
    public void setProductId(UUID value) { productId = value; }
    public UUID getVariantId() { return variantId; }
    public void setVariantId(UUID value) { variantId = value; }
    public BigDecimal getConversion() { return conversion; }
    public void setConversion(BigDecimal value) { conversion = value; }
}
