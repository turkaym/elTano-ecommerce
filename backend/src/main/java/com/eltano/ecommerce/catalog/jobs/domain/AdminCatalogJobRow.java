package com.eltano.ecommerce.catalog.jobs.domain;

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

@Entity
@Table(name = "admin_catalog_job_rows")
public class AdminCatalogJobRow {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false)
    private AdminCatalogJob job;

    @Column(nullable = false)
    private int rowNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AdminCatalogJobRowOutcome outcome;

    @Column(length = 80)
    private String errorCode;

    @Column(length = 1000)
    private String errorMessage;

    @Column(length = 4000)
    private String payloadJson;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public UUID getId() {
        return id;
    }

    public AdminCatalogJob getJob() {
        return job;
    }

    public void setJob(AdminCatalogJob job) {
        this.job = job;
    }

    public int getRowNumber() {
        return rowNumber;
    }

    public void setRowNumber(int rowNumber) {
        this.rowNumber = rowNumber;
    }

    public AdminCatalogJobRowOutcome getOutcome() {
        return outcome;
    }

    public void setOutcome(AdminCatalogJobRowOutcome outcome) {
        this.outcome = outcome;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getPayloadJson() {
        return payloadJson;
    }

    public void setPayloadJson(String payloadJson) {
        this.payloadJson = payloadJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
