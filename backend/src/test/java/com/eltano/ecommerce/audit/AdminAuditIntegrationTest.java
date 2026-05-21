package com.eltano.ecommerce.audit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.eltano.ecommerce.audit.domain.AdminAuditEvent;
import com.eltano.ecommerce.audit.repository.AdminAuditEventRepository;
import com.eltano.ecommerce.catalog.repository.CategoryRepository;
import com.eltano.ecommerce.catalog.repository.ProductRepository;

@SpringBootTest(properties = "app.catalog.seed-on-empty=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminAuditIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AdminAuditEventRepository adminAuditEventRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @BeforeEach
    void setup() {
        adminAuditEventRepository.deleteAll();
        productRepository.deleteAll();
        categoryRepository.deleteAll();
    }

    @Test
    void persistsSuccessAndFailureAuditEventsWithCorrelationId() throws Exception {
        MvcResult success = mockMvc.perform(post("/api/admin/categories")
                .with(httpBasic("admin-user", "admin-pass"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "name": "Audit categoria",
                          "slug": "audit-categoria",
                          "active": true
                        }
                        """))
                .andExpect(status().isCreated())
                .andReturn();

        MvcResult failure = mockMvc.perform(post("/api/admin/categories")
                .with(httpBasic("admin-user", "admin-pass"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "name": "Audit categoria duplicada",
                          "slug": "audit-categoria",
                          "active": true
                        }
                        """))
                .andExpect(status().isConflict())
                .andReturn();

        String successCorrelationId = success.getResponse().getHeader("X-Correlation-Id");
        String failureCorrelationId = failure.getResponse().getHeader("X-Correlation-Id");
        assertNotNull(successCorrelationId);
        assertNotNull(failureCorrelationId);

        AdminAuditEvent successEvent = adminAuditEventRepository.findFirstByCorrelationId(successCorrelationId)
                .orElseThrow();
        AdminAuditEvent failureEvent = adminAuditEventRepository.findFirstByCorrelationId(failureCorrelationId)
                .orElseThrow();

        assertEquals("SUCCESS", successEvent.getOutcome());
        assertEquals("FAILURE", failureEvent.getOutcome());
        assertEquals("admin-user", successEvent.getActor());
        assertEquals("POST", successEvent.getAction());
        assertEquals("categories", successEvent.getEntityType());
        assertTrue(successEvent.getCorrelationId().length() > 10);
        assertTrue(failureEvent.getCorrelationId().length() > 10);
    }
}
