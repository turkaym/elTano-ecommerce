package com.eltano.ecommerce.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
class AdminDisabledSessionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void deniesAdminSessionContractWhenAdminFeatureIsDisabled() throws Exception {
        mockMvc.perform(get("/api/admin/auth/session").with(user("admin-user").roles("ADMIN")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }
}
