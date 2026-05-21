package com.eltano.ecommerce.catalog.jobs.domain;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "admin_catalog_job_inputs")
public class AdminCatalogJobInput {

    @Id
    @Column(name = "job_id", nullable = false)
    private UUID jobId;

    @Column(name = "payload_text", nullable = false, columnDefinition = "text")
    private String payloadText;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public UUID getJobId() {
        return jobId;
    }

    public void setJobId(UUID jobId) {
        this.jobId = jobId;
    }

    public String getPayloadText() {
        return payloadText;
    }

    public void setPayloadText(String payloadText) {
        this.payloadText = payloadText;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
