package com.eltano.ecommerce.audit.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.eltano.ecommerce.audit.domain.AdminAuditEvent;
import com.eltano.ecommerce.audit.repository.AdminAuditEventRepository;

@Service
public class AdminAuditService {

    private final AdminAuditEventRepository adminAuditEventRepository;

    public AdminAuditService(AdminAuditEventRepository adminAuditEventRepository) {
        this.adminAuditEventRepository = adminAuditEventRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AuditCommand command) {
        AdminAuditEvent event = new AdminAuditEvent();
        event.setActor(command.actor());
        event.setAction(command.action());
        event.setEntityType(command.entityType());
        event.setEntityId(command.entityId());
        event.setOutcome(command.outcome());
        event.setCorrelationId(command.correlationId());
        event.setBeforeAfterSummary(command.beforeAfterSummary());
        event.setPath(command.path());
        event.setStatusCode(command.statusCode());

        adminAuditEventRepository.save(event);
    }

    public record AuditCommand(
            String actor,
            String action,
            String entityType,
            String entityId,
            String outcome,
            String correlationId,
            String beforeAfterSummary,
            String path,
            int statusCode) {
    }
}
