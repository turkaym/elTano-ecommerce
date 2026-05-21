package com.eltano.ecommerce.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "app.admin.enabled=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminFeatureToggleIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void deniesAdminEndpointsWhenAdminFeatureDisabled() throws Exception {
        mockMvc.perform(get("/api/admin/categories")
                .with(httpBasic("admin-user", "admin-pass")))
                .andExpect(status().isForbidden());
    }
}
