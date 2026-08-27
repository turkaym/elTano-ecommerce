package com.eltano.ecommerce.procurement.api;

import java.io.IOException;
import java.util.Set;
import java.util.UUID;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.eltano.ecommerce.audit.service.AdminAuditService;
import com.eltano.ecommerce.common.api.CorrelationIdFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class UnauthorizedProcurementAuditFilter extends OncePerRequestFilter {
    private static final String PREFIX = "/api/admin/procurement/";
    private static final Set<String> MUTATIONS = Set.of("POST", "PUT", "PATCH", "DELETE");
    private final AdminAuditService audit;

    public UnauthorizedProcurementAuditFilter(AdminAuditService audit) { this.audit = audit; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        chain.doFilter(request, response);
        if (isDeniedMutation(request, response)) {
            audit.record(new AdminAuditService.AuditCommand(
                    "anonymous", request.getMethod(), "procurement", null, "FAILURE", correlation(request),
                    "Unauthorized procurement mutation denied", request.getRequestURI(), response.getStatus()));
        }
    }

    private boolean isDeniedMutation(HttpServletRequest request, HttpServletResponse response) {
        return request.getRequestURI() != null && request.getRequestURI().startsWith(PREFIX)
                && MUTATIONS.contains(request.getMethod())
                && (response.getStatus() == HttpServletResponse.SC_UNAUTHORIZED
                    || response.getStatus() == HttpServletResponse.SC_FORBIDDEN);
    }

    private String correlation(HttpServletRequest request) {
        Object value = request.getAttribute(CorrelationIdFilter.REQUEST_ATTRIBUTE);
        return value instanceof String text && !text.isBlank() ? text : UUID.randomUUID().toString();
    }
}
