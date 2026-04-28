package com.eltano.ecommerce.audit.repository;

import java.util.UUID;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.eltano.ecommerce.audit.domain.AdminAuditEvent;

public interface AdminAuditEventRepository extends JpaRepository<AdminAuditEvent, UUID> {

    Optional<AdminAuditEvent> findFirstByCorrelationId(String correlationId);
}
