package com.foodmate.api.controller;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodmate.api.advice.GlobalExceptionHandler;
import com.foodmate.api.filter.CsrfProtectionFilter;
import com.foodmate.api.filter.TraceContextFilter;
import com.foodmate.application.account.UserAccountService;
import com.foodmate.shared.id.IdGenerator;
import com.foodmate.shared.id.SnowflakeIdGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({AuthController.class, UserController.class, SessionController.class, CsrfProtectionFilter.class})
@Import({UserAccountService.class, GlobalExceptionHandler.class, TraceContextFilter.class, P1AccountControllerTest.Config.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class P1AccountControllerTest {
    @Autowired MockMvc mockMvc;

    @Test
    void persistsConversationUsingCookieSessionAndCsrfProtection() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"p1\",\"email\":\"p1@example.com\",\"password\":\"password123\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.user_id").exists());
        var login = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"username_or_email\":\"p1\",\"password\":\"password123\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.session_expires_at").exists()).andExpect(jsonPath("$.data.access_token").doesNotExist()).andReturn().getResponse();
        var sessionCookie = login.getCookie("foodmate_session");
        var csrfCookie = login.getCookie("foodmate_csrf");

        String conversation = mockMvc.perform(post("/api/sessions").cookie(sessionCookie).header("X-CSRF-Token", csrfCookie.getValue()).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"test\",\"mode\":\"agent\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.session_id").exists()).andReturn().getResponse().getContentAsString();
        String sessionId = conversation.replaceAll(".*\\\"session_id\\\":([0-9]+).*", "$1");

        mockMvc.perform(post("/api/sessions/" + sessionId + "/messages").cookie(sessionCookie).header("X-CSRF-Token", csrfCookie.getValue()).contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"user\",\"content\":\"hello\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.sequence_no", is(1)));
        mockMvc.perform(get("/api/sessions/" + sessionId + "/messages").cookie(sessionCookie))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].content", is("hello")));
    }

    @Test
    void rejectsMutationWithoutCsrfToken() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"p2\",\"email\":\"p2@example.com\",\"password\":\"password123\"}"));
        var login = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"username_or_email\":\"p2\",\"password\":\"password123\"}"))
                .andReturn().getResponse();
        mockMvc.perform(post("/api/sessions").cookie(login.getCookie("foodmate_session")).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"test\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code", is("FORBIDDEN")));
    }

    @Test
    void protectedEndpointRequiresSessionCookie() throws Exception {
        mockMvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error.code", is("AUTH_REQUIRED")));
    }

    @TestConfiguration
    static class Config {
        @Bean IdGenerator idGenerator() { return new SnowflakeIdGenerator(2); }
    }
}
