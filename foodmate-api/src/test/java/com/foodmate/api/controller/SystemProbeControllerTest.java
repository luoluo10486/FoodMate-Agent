package com.foodmate.api.controller;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodmate.api.advice.GlobalExceptionHandler;
import com.foodmate.api.filter.TraceContextFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SystemProbeController.class)
@Import({GlobalExceptionHandler.class, TraceContextFilter.class})
class SystemProbeControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void returnsStructuredPingResponseWithTraceHeaders() throws Exception {
        mockMvc.perform(get("/foodmate/_system/ping")
                        .param("echo", "hello")
                        .header(TraceContextFilter.REQUEST_ID_HEADER, "req_mock")
                        .header(TraceContextFilter.TRACE_ID_HEADER, "trace_mock"))
                .andExpect(status().isOk())
                .andExpect(header().string(TraceContextFilter.REQUEST_ID_HEADER, "req_mock"))
                .andExpect(header().string(TraceContextFilter.TRACE_ID_HEADER, "trace_mock"))
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.status", is("ok")))
                .andExpect(jsonPath("$.data.echo", is("hello")))
                .andExpect(jsonPath("$.meta.request_id", is("req_mock")))
                .andExpect(jsonPath("$.meta.trace_id", is("trace_mock")));
    }

    @Test
    void mapsParameterValidationErrors() throws Exception {
        mockMvc.perform(get("/foodmate/_system/ping")
                        .param("echo", "x".repeat(65))
                        .header(TraceContextFilter.REQUEST_ID_HEADER, "req_mock")
                        .header(TraceContextFilter.TRACE_ID_HEADER, "trace_mock"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success", is(false)))
                .andExpect(jsonPath("$.error.code", is("INVALID_ARGUMENT")))
                .andExpect(jsonPath("$.meta.request_id", is("req_mock")));
    }
}

