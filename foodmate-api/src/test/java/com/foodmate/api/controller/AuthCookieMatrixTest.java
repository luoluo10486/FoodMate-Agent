package com.foodmate.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.foodmate.application.account.UserAccountService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AuthCookieMatrixTest {
    private static final Instant EXPIRY = Instant.now().plusSeconds(3600);

    @Test
    void productionCookiesHaveSecureHttpOnlyAndSameSiteAttributes() throws Exception {
        UserAccountService service = mockService();
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new AuthController(service, true)).build();

        List<String> cookies = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username_or_email\":\"alice\",\"password\":\"password123\"}"))
                .andReturn().getResponse().getHeaders("Set-Cookie");

        String session = cookies.stream().filter(value -> value.startsWith("foodmate_session=")).findFirst().orElseThrow();
        String csrf = cookies.stream().filter(value -> value.startsWith("foodmate_csrf=")).findFirst().orElseThrow();
        assertThat(session).contains("Secure", "HttpOnly", "SameSite=Lax", "Path=/");
        assertThat(csrf).contains("Secure", "SameSite=Lax", "Path=/").doesNotContain("HttpOnly");
    }

    @Test
    void localCookiesMayOmitSecureButKeepOtherAttributes() throws Exception {
        List<String> cookies = MockMvcBuilders.standaloneSetup(new AuthController(mockService(), false)).build()
                .perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username_or_email\":\"alice\",\"password\":\"password123\"}"))
                .andReturn().getResponse().getHeaders("Set-Cookie");
        assertThat(cookies).allMatch(value -> !value.contains("Secure") && value.contains("SameSite=Lax") && value.contains("Path=/"));
    }

    @Test
    void logoutExpiresAllAuthenticationCookies() throws Exception {
        List<String> cookies = MockMvcBuilders.standaloneSetup(new AuthController(mockService(), true)).build()
                .perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/logout")
                        .cookie(new jakarta.servlet.http.Cookie("foodmate_session", "session-token")))
                .andReturn().getResponse().getHeaders("Set-Cookie");
        assertThat(cookies).hasSize(4).allMatch(value -> value.contains("Max-Age=0") && value.contains("Secure") && value.contains("SameSite=Lax") && value.contains("Path=/"));
    }

    private static UserAccountService mockService() {
        UserAccountService service = mock(UserAccountService.class);
        when(service.login(eq("alice"), eq("password123"), any(UserAccountService.SessionMetadata.class)))
                .thenReturn(new UserAccountService.AuthResult(1L, "alice", "user", "session-token", "csrf-token", EXPIRY));
        return service;
    }
}
