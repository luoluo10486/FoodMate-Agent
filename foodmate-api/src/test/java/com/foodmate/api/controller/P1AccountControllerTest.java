package com.foodmate.api.controller;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodmate.api.advice.GlobalExceptionHandler;
import com.foodmate.api.filter.TraceContextFilter;
import com.foodmate.application.account.UserAccountService;
import com.foodmate.shared.id.IdGenerator;
import com.foodmate.shared.id.SnowflakeIdGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;

@WebMvcTest({AuthController.class, UserController.class, SessionController.class})
@Import({UserAccountService.class, GlobalExceptionHandler.class, TraceContextFilter.class, P1AccountControllerTest.Config.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class P1AccountControllerTest {
    @Autowired MockMvc mockMvc;

    @Test
    void registersLogsInAndPersistsConversationInLocalStore() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"p1\",\"email\":\"p1@example.com\",\"password\":\"password123\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.user_id").exists());
        String login = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"username_or_email\":\"p1\",\"password\":\"password123\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.access_token").exists()).andReturn().getResponse().getContentAsString();
        String token = login.replaceAll(".*\\\"access_token\\\":\\\"([^\\\"]+)\\\".*", "$1");
        var headers = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/sessions").header("Authorization", "Bearer " + token);
        String session = mockMvc.perform(headers.contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"测试会话\",\"mode\":\"agent\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.session_id").exists()).andReturn().getResponse().getContentAsString();
        String sessionId = session.replaceAll(".*\\\"session_id\\\":([0-9]+).*", "$1");
        mockMvc.perform(post("/api/sessions/" + sessionId + "/messages").header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"user\",\"content\":\"hello\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.sequence_no", is(1)));
        mockMvc.perform(get("/api/sessions/" + sessionId + "/messages").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].content", is("hello")));
    }

    @Test
    void protectedEndpointRequiresBearerToken() throws Exception {
        mockMvc.perform(get("/api/users/me")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error.code", is("AUTH_REQUIRED")));
    }

    @TestConfiguration
    static class Config {
        @Bean IdGenerator idGenerator() { return new SnowflakeIdGenerator(2); }
    }
}
