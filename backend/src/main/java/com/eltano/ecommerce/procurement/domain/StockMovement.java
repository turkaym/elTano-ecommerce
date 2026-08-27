package com.eltano.ecommerce.procurement.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

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
@Table(name = "stock_movements", uniqueConstraints = @UniqueConstraint(name = "uk_stock_movement_source", columnNames = {"source_type", "source_id"}))
public class StockMovement {
    @Id @GeneratedValue(strategy = GenerationType.UUID) private UUID id;
    @Column(nullable = false, length = 30) private String sourceType;
    @Column(nullable = false) private UUID sourceId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "purchase_id") private Purchase purchase;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "receipt_id") private PurchaseReceipt receipt;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private InventoryTargetType targetType;
    @Column(nullable = false) private UUID targetId;
    @Column(nullable = false, precision = 18, scale = 6) private BigDecimal quantity;
    @Column(nullable = false, precision = 18, scale = 6) private BigDecimal conversion;
    @Column(nullable = false) private int canonicalDelta;
    @Column(nullable = false) private int beforeBalance;
    @Column(nullable = false) private int afterBalance;
    @Column(nullable = false, length = 120) private String actor;
    @Column(nullable = false, length = 120) private String correlationId;
    @CreationTimestamp @Column(nullable = false, updatable = false) private Instant createdAt;
    public UUID getId() { return id; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String value) { sourceType = value; }
    public UUID getSourceId() { return sourceId; }
    public void setSourceId(UUID value) { sourceId = value; }
    public Purchase getPurchase() { return purchase; }
    public void setPurchase(Purchase value) { purchase = value; }
    public PurchaseReceipt getReceipt() { return receipt; }
    public void setReceipt(PurchaseReceipt value) { receipt = value; }
    public InventoryTargetType getTargetType() { return targetType; }
    public void setTargetType(InventoryTargetType value) { targetType = value; }
    public UUID getTargetId() { return targetId; }
    public void setTargetId(UUID value) { targetId = value; }
    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal value) { quantity = value; }
    public BigDecimal getConversion() { return conversion; }
    public void setConversion(BigDecimal value) { conversion = value; }
    public int getCanonicalDelta() { return canonicalDelta; }
    public void setCanonicalDelta(int value) { canonicalDelta = value; }
    public int getBeforeBalance() { return beforeBalance; }
    public void setBeforeBalance(int value) { beforeBalance = value; }
    public int getAfterBalance() { return afterBalance; }
    public void setAfterBalance(int value) { afterBalance = value; }
    public String getActor() { return actor; }
    public void setActor(String value) { actor = value; }
    public String getCorrelationId() { return correlationId; }
    public void setCorrelationId(String value) { correlationId = value; }
}
