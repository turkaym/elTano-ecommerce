package com.eltano.ecommerce.procurement.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

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
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "supplier_item_mappings", uniqueConstraints = {
        @UniqueConstraint(name = "uk_supplier_item_mapping_code", columnNames = {"supplier_id", "normalized_code"}),
        @UniqueConstraint(name = "uk_supplier_item_mapping_name", columnNames = {"supplier_id", "normalized_name"})})
public class SupplierItemMapping {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "supplier_id") private Supplier supplier;
    @Column(length = 180) private String supplierItemCode;
    @Column(length = 180) private String normalizedCode;
    @Column(length = 500) private String supplierItemName;
    @Column(length = 500) private String normalizedName;
    @Column(nullable = false, length = 500) private String description;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private InventoryTargetType targetType;
    private UUID productId;
    private UUID variantId;
    @Column(nullable = false, precision = 18, scale = 6) private BigDecimal defaultConversion;
    @Column(nullable = false) private boolean active = true;
    @CreationTimestamp @Column(nullable = false, updatable = false) private Instant createdAt;
    @UpdateTimestamp @Column(nullable = false) private Instant updatedAt;
    public UUID getId() { return id; }
    public Supplier getSupplier() { return supplier; }
    public void setSupplier(Supplier value) { supplier = value; }
    public String getSupplierItemCode() { return supplierItemCode; }
    public void setSupplierItemCode(String value) { supplierItemCode = value; }
    public String getNormalizedCode() { return normalizedCode; }
    public void setNormalizedCode(String value) { normalizedCode = value; }
    public String getSupplierItemName() { return supplierItemName; }
    public void setSupplierItemName(String value) { supplierItemName = value; }
    public String getNormalizedName() { return normalizedName; }
    public void setNormalizedName(String value) { normalizedName = value; }
    public String getDescription() { return description; }
    public void setDescription(String value) { description = value; }
    public InventoryTargetType getTargetType() { return targetType; }
    public void setTargetType(InventoryTargetType value) { targetType = value; }
    public UUID getProductId() { return productId; }
    public void setProductId(UUID value) { productId = value; }
    public UUID getVariantId() { return variantId; }
    public void setVariantId(UUID value) { variantId = value; }
    public BigDecimal getDefaultConversion() { return defaultConversion; }
    public void setDefaultConversion(BigDecimal value) { defaultConversion = value; }
    public boolean isActive() { return active; }
    public void setActive(boolean value) { active = value; }
}
