package com.eltano.ecommerce.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void deniesAnonymousAdminAccessWith401() throws Exception {
        mockMvc.perform(get("/api/admin/categories"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deniesNonAdminUserWith403() throws Exception {
        mockMvc.perform(get("/api/admin/categories")
                .with(httpBasic("storefront-user", "storefront-pass")))
                .andExpect(status().isForbidden());
    }

    @Test
    void allowsAdminUserWith200() throws Exception {
        mockMvc.perform(get("/api/admin/categories")
                .with(httpBasic("admin-user", "admin-pass")))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsExpiredAdminCredentialWith401() throws Exception {
        mockMvc.perform(get("/api/admin/categories")
                .with(httpBasic("expired-admin", "expired-pass")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void requiresCsrfTokenForAdminMutatingEndpoint() throws Exception {
        mockMvc.perform(post("/api/admin/categories")
                .with(httpBasic("admin-user", "admin-pass"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "name": "Nueces",
                          "slug": "nueces",
                          "active": true
                        }
                        """))
                .andExpect(status().isForbidden());
    }

    @Test
    void allowsAdminMutatingEndpointWithCsrfToken() throws Exception {
        mockMvc.perform(post("/api/admin/categories")
                .with(httpBasic("admin-user", "admin-pass"))
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "name": "Semillas",
                          "slug": "semillas-admin-sec-test",
                          "active": true
                        }
                        """))
                .andExpect(status().isCreated());
    }
}
