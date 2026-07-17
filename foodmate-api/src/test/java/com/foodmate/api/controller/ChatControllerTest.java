package com.foodmate.api.controller;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodmate.api.advice.GlobalExceptionHandler;
import com.foodmate.api.filter.TraceContextFilter;
import com.foodmate.application.runtime.RuntimeGatewayService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({ChatController.class, RunStreamController.class})
@Import({RuntimeGatewayService.class, GlobalExceptionHandler.class, TraceContextFilter.class})
class ChatControllerTest {
    @Autowired private MockMvc mockMvc;

    @Test void createsRunAndExposesStatus() throws Exception {
        String response = mockMvc.perform(post("/api/chat/runs").contentType(MediaType.APPLICATION_JSON).content("{\"prompt\":\"分析本周饮食\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status", is("DISPATCHED")))
                .andReturn().getResponse().getContentAsString();
        String runId = response.replaceAll(".*\\\"run_id\\\":\\\"([^\\\"]+)\\\".*", "$1");
        mockMvc.perform(get("/api/chat/runs/" + runId)).andExpect(status().isOk()).andExpect(jsonPath("$.data.status", is("DISPATCHED")));
        mockMvc.perform(get("/api/chat/runs/" + runId + "/events")).andExpect(status().isOk()).andExpect(jsonPath("$.data", is(java.util.List.of())));
    }

    @Test void rejectsBlankPrompt() throws Exception {
        mockMvc.perform(post("/api/chat/runs").contentType(MediaType.APPLICATION_JSON).content("{\"prompt\":\" \"}"))
                .andExpect(status().isBadRequest());
    }
}
