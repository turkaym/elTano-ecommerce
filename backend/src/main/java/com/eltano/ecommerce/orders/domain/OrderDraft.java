package com.eltano.ecommerce.orders.domain;

import java.math.BigDecimal;
import java.time.Instant;
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
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "order_drafts", uniqueConstraints = {
        @UniqueConstraint(name = "uk_order_drafts_reference", columnNames = "reference")
})
public class OrderDraft {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 40)
    private String reference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderDraftStatus status;

    @Column(nullable = false, length = 180)
    private String customerName;

    @Column(nullable = false, length = 60)
    private String phone;

    @Column(length = 1000)
    private String note;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private FulfillmentMethod fulfillmentMethod = FulfillmentMethod.PICKUP;

    @Column(length = 500)
    private String deliveryAddress;

    @Column(length = 120)
    private String pickupTime;

    @Column(nullable = false, length = 10)
    private String currency;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    @Column(length = 40)
    private String paymentProvider;

    @Column(length = 120)
    private String paymentPreferenceId;

    @Column(length = 120)
    private String paymentExternalId;

    @Column(length = 120)
    private String paymentStatusDetail;

    @Column
    private Instant paymentUpdatedAt;

    @Column
    private Instant stockReleasedAt;

    @OneToMany(mappedBy = "orderDraft", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderDraftLine> lines = new ArrayList<>();

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    public UUID getId() {
        return id;
    }

    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    public OrderDraftStatus getStatus() {
        return status;
    }

    public void setStatus(OrderDraftStatus status) {
        this.status = status;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public FulfillmentMethod getFulfillmentMethod() {
        return fulfillmentMethod;
    }

    public void setFulfillmentMethod(FulfillmentMethod fulfillmentMethod) {
        this.fulfillmentMethod = fulfillmentMethod;
    }

    public String getDeliveryAddress() {
        return deliveryAddress;
    }

    public void setDeliveryAddress(String deliveryAddress) {
        this.deliveryAddress = deliveryAddress;
    }

    public String getPickupTime() {
        return pickupTime;
    }

    public void setPickupTime(String pickupTime) {
        this.pickupTime = pickupTime;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public void setSubtotal(BigDecimal subtotal) {
        this.subtotal = subtotal;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public void setTotal(BigDecimal total) {
        this.total = total;
    }

    public List<OrderDraftLine> getLines() {
        return lines;
    }

    public void addLine(OrderDraftLine line) {
        line.setOrderDraft(this);
        lines.add(line);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public String getPaymentProvider() {
        return paymentProvider;
    }

    public void setPaymentProvider(String paymentProvider) {
        this.paymentProvider = paymentProvider;
    }

    public String getPaymentPreferenceId() {
        return paymentPreferenceId;
    }

    public void setPaymentPreferenceId(String paymentPreferenceId) {
        this.paymentPreferenceId = paymentPreferenceId;
    }

    public String getPaymentExternalId() {
        return paymentExternalId;
    }

    public void setPaymentExternalId(String paymentExternalId) {
        this.paymentExternalId = paymentExternalId;
    }

    public String getPaymentStatusDetail() {
        return paymentStatusDetail;
    }

    public void setPaymentStatusDetail(String paymentStatusDetail) {
        this.paymentStatusDetail = paymentStatusDetail;
    }

    public Instant getPaymentUpdatedAt() {
        return paymentUpdatedAt;
    }

    public void setPaymentUpdatedAt(Instant paymentUpdatedAt) {
        this.paymentUpdatedAt = paymentUpdatedAt;
    }

    public Instant getStockReleasedAt() {
        return stockReleasedAt;
    }

    public void setStockReleasedAt(Instant stockReleasedAt) {
        this.stockReleasedAt = stockReleasedAt;
    }
}
