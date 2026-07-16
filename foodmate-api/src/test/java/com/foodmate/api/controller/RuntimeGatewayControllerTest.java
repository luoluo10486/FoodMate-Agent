package com.foodmate.api.controller;

import static org.hamcrest.Matchers.is;
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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(RuntimeGatewayController.class)
@Import({RuntimeGatewayService.class, GlobalExceptionHandler.class, TraceContextFilter.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class RuntimeGatewayControllerTest {
    @Autowired private MockMvc mockMvc;

    private static final String DISPATCH = "{" +
            "\"dispatchId\":\"d-http\",\"runId\":\"r-http\",\"input\":\"hello\"," +
            "\"deadlineAt\":\"2099-01-01T00:00:00Z\",\"attempt\":1}";

    @Test void dispatchRetryIsDuplicateAndChangedBodyConflicts() throws Exception {
        mockMvc.perform(post("/internal/runtime/runs:dispatch").contentType(MediaType.APPLICATION_JSON).content(DISPATCH))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.duplicate", is(false)));
        mockMvc.perform(post("/internal/runtime/runs:dispatch").contentType(MediaType.APPLICATION_JSON).content(DISPATCH))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.duplicate", is(true)));
        mockMvc.perform(post("/internal/runtime/runs:dispatch").contentType(MediaType.APPLICATION_JSON)
                        .content(DISPATCH.replace("hello", "changed")))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error.code", is("CONFLICT")));
    }

    @Test void eventChainReachesSucceededAndRejectsRollback() throws Exception {
        mockMvc.perform(post("/internal/runtime/runs:dispatch").contentType(MediaType.APPLICATION_JSON).content(DISPATCH));
        postEvent("e-http-1", 1, "DISPATCHED", null, false);
        postEvent("e-http-2", 2, "RUNNING", null, false);
        postEvent("e-http-3", 3, "SUCCEEDED", "ok", false);
        postEvent("e-http-4", 4, "RUNNING", null, true);
    }

    private void postEvent(String id, int seq, String state, String payload, boolean conflict) throws Exception {
        String json = "{\"eventId\":\"" + id + "\",\"runId\":\"r-http\",\"eventSeq\":" + seq
                + ",\"state\":\"" + state + "\",\"payload\":" + (payload == null ? "null" : "\"" + payload + "\"")
                + ",\"occurredAt\":\"2099-01-01T00:00:00Z\"}";
        var result = mockMvc.perform(post("/internal/runtime/runs:events").contentType(MediaType.APPLICATION_JSON).content(json));
        if (conflict) result.andExpect(status().isConflict()).andExpect(jsonPath("$.error.code", is("CONFLICT")));
        else result.andExpect(status().isOk()).andExpect(jsonPath("$.success", is(true)));
    }
}
