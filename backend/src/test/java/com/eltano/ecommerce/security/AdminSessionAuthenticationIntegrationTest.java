package com.eltano.ecommerce.security;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.eltano.ecommerce.audit.repository.AdminAuditEventRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
class AdminSessionAuthenticationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AdminAuditEventRepository auditEventRepository;

    @Test
    void bootstrapsAnonymousCsrfCookieWithoutCaching() throws Exception {
        mockMvc.perform(get("/api/admin/auth/csrf"))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(cookie().exists("XSRF-TOKEN"));
    }

    @Test
    void rejectsLoginWithoutCsrf() throws Exception {
        mockMvc.perform(post("/api/admin/auth/login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "admin-user")
                        .param("password", "admin-pass"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("CSRF_FORBIDDEN"));
    }

    @Test
    void returnsGenericJsonForWrongCredentials() throws Exception {
        CsrfExchange csrf = csrfExchange();
        long auditCount = auditEventRepository.count();

        mockMvc.perform(post("/api/admin/auth/login")
                        .cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.cookie().getValue())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "admin-user")
                        .param("password", "wrong-password"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("Usuario o contrasena invalidos"));

        org.junit.jupiter.api.Assertions.assertEquals(auditCount, auditEventRepository.count());
    }

    @Test
    void changesExistingSessionIdAfterSuccessfulLogin() throws Exception {
        CsrfExchange csrf = csrfExchange();
        MockHttpSession existingSession = new MockHttpSession();
        String previousSessionId = existingSession.getId();

        mockMvc.perform(post("/api/admin/auth/login")
                        .session(existingSession)
                        .cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.cookie().getValue())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "admin-user")
                        .param("password", "admin-pass"))
                .andExpect(status().isNoContent());

        assertNotEquals(previousSessionId, existingSession.getId());
    }

    @Test
    void authenticatesWithSessionAndAllowsReadsAndCsrfProtectedWritesWithoutBasic() throws Exception {
        AuthenticatedExchange authenticated = login();

        MvcResult sessionResult = mockMvc.perform(get("/api/admin/auth/session")
                        .session(authenticated.session()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.authenticated").value(true))
                .andExpect(jsonPath("$.username").value("admin-user"))
                .andExpect(jsonPath("$.roles[0]").value("ADMIN"))
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andReturn();

        Cookie csrfCookie = sessionResult.getResponse().getCookie("XSRF-TOKEN");
        assertNotEquals(authenticated.preLoginCsrfToken(), csrfCookie.getValue());
        mockMvc.perform(get("/api/admin/categories").session(authenticated.session()))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/categories")
                        .session(authenticated.session())
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Session Category",
                                  "slug": "session-category-auth-test",
                                  "active": true
                                }
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    void rejectsSessionWriteWithoutCsrfButKeepsSessionAuthenticated() throws Exception {
        AuthenticatedExchange authenticated = login();

        mockMvc.perform(post("/api/admin/categories")
                        .session(authenticated.session())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Rejected","slug":"missing-session-csrf","active":true}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CSRF_FORBIDDEN"));

        mockMvc.perform(get("/api/admin/auth/session").session(authenticated.session()))
                .andExpect(status().isOk());
    }

    @Test
    void logoutInvalidatesSessionAndClearsAuthenticationCookies() throws Exception {
        AuthenticatedExchange authenticated = login();
        MvcResult sessionResult = mockMvc.perform(get("/api/admin/auth/session")
                        .session(authenticated.session()))
                .andReturn();
        Cookie csrfCookie = sessionResult.getResponse().getCookie("XSRF-TOKEN");

        mockMvc.perform(post("/api/admin/auth/logout")
                        .session(authenticated.session())
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue()))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(cookie().maxAge("JSESSIONID", 0))
                .andExpect(cookie().maxAge("XSRF-TOKEN", 0));

        mockMvc.perform(get("/api/admin/auth/session").session(authenticated.session()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    private CsrfExchange csrfExchange() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/admin/auth/csrf"))
                .andExpect(status().isNoContent())
                .andReturn();
        return new CsrfExchange(result.getResponse().getCookie("XSRF-TOKEN"));
    }

    private AuthenticatedExchange login() throws Exception {
        CsrfExchange csrf = csrfExchange();
        MvcResult result = mockMvc.perform(post("/api/admin/auth/login")
                        .cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.cookie().getValue())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "admin-user")
                        .param("password", "admin-pass"))
                .andExpect(status().isNoContent())
                .andReturn();
        return new AuthenticatedExchange(
                (MockHttpSession) result.getRequest().getSession(false),
                csrf.cookie().getValue());
    }

    private record CsrfExchange(Cookie cookie) {
    }

    private record AuthenticatedExchange(MockHttpSession session, String preLoginCsrfToken) {
    }
}
