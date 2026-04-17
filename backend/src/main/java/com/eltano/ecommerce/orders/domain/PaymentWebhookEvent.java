package com.eltano.ecommerce.orders.domain;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "payment_webhook_events", uniqueConstraints = {
        @UniqueConstraint(name = "uk_payment_webhook_events_provider_event", columnNames = "provider_event_id")
})
public class PaymentWebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 40)
    private String provider;

    @Column(nullable = false, length = 120)
    private String providerEventId;

    @Column(length = 120)
    private String paymentExternalId;

    @Column(length = 128)
    private String payloadHash;

    @Column(nullable = false, length = 80)
    private String outcome;

    @Column(nullable = false)
    private Instant processedAt;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onPersist() {
        if (processedAt == null) {
            processedAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getProviderEventId() {
        return providerEventId;
    }

    public void setProviderEventId(String providerEventId) {
        this.providerEventId = providerEventId;
    }

    public String getPaymentExternalId() {
        return paymentExternalId;
    }

    public void setPaymentExternalId(String paymentExternalId) {
        this.paymentExternalId = paymentExternalId;
    }

    public String getPayloadHash() {
        return payloadHash;
    }

    public void setPayloadHash(String payloadHash) {
        this.payloadHash = payloadHash;
    }

    public String getOutcome() {
        return outcome;
    }

    public void setOutcome(String outcome) {
        this.outcome = outcome;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(Instant processedAt) {
        this.processedAt = processedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
