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
@Table(name = "purchase_lines")
public class PurchaseLine {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "purchase_id") private Purchase purchase;
    private UUID mappingId;
    @Column(nullable = false, length = 180) private String supplierItemCode;
    @Column(nullable = false, length = 500) private String supplierDescription;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private InventoryTargetType targetType;
    private UUID productId;
    private UUID variantId;
    @Column(nullable = false, precision = 18, scale = 6) private BigDecimal orderedQuantity;
    @Column(nullable = false, precision = 18, scale = 6) private BigDecimal conversion;
    public UUID getId() { return id; }
    public Purchase getPurchase() { return purchase; }
    public void setPurchase(Purchase value) { purchase = value; }
    public UUID getMappingId() { return mappingId; }
    public void setMappingId(UUID value) { mappingId = value; }
    public String getSupplierItemCode() { return supplierItemCode; }
    public void setSupplierItemCode(String value) { supplierItemCode = value; }
    public String getSupplierDescription() { return supplierDescription; }
    public void setSupplierDescription(String value) { supplierDescription = value; }
    public InventoryTargetType getTargetType() { return targetType; }
    public void setTargetType(InventoryTargetType value) { targetType = value; }
    public UUID getProductId() { return productId; }
    public void setProductId(UUID value) { productId = value; }
    public UUID getVariantId() { return variantId; }
    public void setVariantId(UUID value) { variantId = value; }
    public BigDecimal getOrderedQuantity() { return orderedQuantity; }
    public void setOrderedQuantity(BigDecimal value) { orderedQuantity = value; }
    public BigDecimal getConversion() { return conversion; }
    public void setConversion(BigDecimal value) { conversion = value; }
}
