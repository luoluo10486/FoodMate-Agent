package com.foodmate.api.controller;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({AuthController.class, SessionController.class, CsrfProtectionFilter.class})
@Import({UserAccountService.class, GlobalExceptionHandler.class, TraceContextFilter.class, M12SessionControllerTest.Config.class})
class M12SessionControllerTest {
    @Autowired MockMvc mockMvc;

    @Test
    void supportsPagingSoftDeleteRestoreAndUserOnlyMessages() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"m12\",\"email\":\"m12@example.com\",\"password\":\"password123\"}"));
        var login = mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"username_or_email\":\"m12\",\"password\":\"password123\"}" )).andReturn().getResponse();
        var session = login.getCookie("foodmate_session"); var csrf = login.getCookie("foodmate_csrf");
        var created = mockMvc.perform(post("/api/sessions").cookie(session).header("X-CSRF-Token", csrf.getValue()).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"M1-2\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String id = created.replaceAll(".*\\\"session_id\\\":([0-9]+).*", "$1");
        mockMvc.perform(post("/api/sessions/" + id + "/messages").cookie(session).header("X-CSRF-Token", csrf.getValue()).contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"assistant\",\"content\":\"no\"}"))
                .andExpect(status().is4xxClientError());
        mockMvc.perform(post("/api/sessions/" + id + "/messages").cookie(session).header("X-CSRF-Token", csrf.getValue()).contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"user\",\"content\":\"hello\"}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/sessions?size=1").cookie(session)).andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].title", is("M1-2"))).andExpect(jsonPath("$.data.total", is(1)));
        mockMvc.perform(delete("/api/sessions/" + id).cookie(session).header("X-CSRF-Token", csrf.getValue())).andExpect(status().isOk());
        mockMvc.perform(get("/api/sessions/deleted").cookie(session)).andExpect(status().isOk()).andExpect(jsonPath("$.data.items[0].status", is("deleted")));
        mockMvc.perform(post("/api/sessions/" + id + "/restore").cookie(session).header("X-CSRF-Token", csrf.getValue())).andExpect(status().isOk());
    }

    @TestConfiguration
    static class Config { @Bean IdGenerator idGenerator() { return new SnowflakeIdGenerator(2); } }
}
