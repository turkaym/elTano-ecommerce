package com.eltano.ecommerce.audit.api;

import java.util.Set;
import java.util.UUID;

import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.eltano.ecommerce.audit.service.AdminAuditService;
import com.eltano.ecommerce.common.api.CorrelationIdFilter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class AdminAuditInterceptor implements HandlerInterceptor {

    private static final String ADMIN_PREFIX = "/api/admin/";
    private static final Set<String> MUTATING_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final AdminAuditService adminAuditService;

    public AdminAuditInterceptor(AdminAuditService adminAuditService) {
        this.adminAuditService = adminAuditService;
    }

    @Override
    public void afterCompletion(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull Object handler,
            Exception ex) {
        if (!shouldAudit(request)) {
            return;
        }

        ParsedTarget target = parseTarget(request.getRequestURI());
        int statusCode = response.getStatus();
        String outcome = statusCode < 400 ? "SUCCESS" : "FAILURE";

        adminAuditService.record(new AdminAuditService.AuditCommand(
                actor(),
                request.getMethod(),
                target.entityType(),
                target.entityId(),
                outcome,
                correlationId(request),
                null,
                request.getRequestURI(),
                statusCode));
    }

    private boolean shouldAudit(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (path == null || !path.startsWith(ADMIN_PREFIX)) {
            return false;
        }
        return MUTATING_METHODS.contains(request.getMethod());
    }

    private String actor() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            return "anonymous";
        }
        return authentication.getName();
    }

    private String correlationId(HttpServletRequest request) {
        Object value = request.getAttribute(CorrelationIdFilter.REQUEST_ATTRIBUTE);
        if (value instanceof String text && !text.isBlank()) {
            return text;
        }
        return UUID.randomUUID().toString();
    }

    private ParsedTarget parseTarget(String path) {
        if (path == null || path.isBlank()) {
            return new ParsedTarget("unknown", null);
        }

        String[] segments = path.split("/");
        if (segments.length < 4) {
            return new ParsedTarget("unknown", null);
        }

        String entityType = segments[3];
        String entityId = segments.length > 4 ? segments[4] : null;
        return new ParsedTarget(entityType, entityId);
    }

    private record ParsedTarget(String entityType, String entityId) {
    }
}
