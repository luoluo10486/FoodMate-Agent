package com.foodmate.api.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodmate.api.advice.GlobalExceptionHandler;
import com.foodmate.api.controller.runtime.AgentFeedbackController;
import com.foodmate.application.account.service.UserAccountService;
import com.foodmate.application.runtime.service.AgentFeedbackService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AgentFeedbackControllerTest {
    private final UserAccountService accounts = mock(UserAccountService.class);
    private final AgentFeedbackService feedback = mock(AgentFeedbackService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        when(accounts.requireSessionUser("session-token"))
                .thenReturn(
                        new UserAccountService.UserRecord(
                                7L,
                                "foodmate-user",
                                "user@example.com",
                                "hash",
                                "FoodMate User",
                                "user",
                                "active"));
        mockMvc =
                MockMvcBuilders.standaloneSetup(new AgentFeedbackController(accounts, feedback))
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .build();
    }

    @Test
    void authenticatesAndPassesStructuredFeedback() throws Exception {
        when(feedback.submit(anyLong(), anyLong(), anyLong(), any()))
                .thenReturn(
                        new AgentFeedbackService.FeedbackResult(
                                100L,
                                7L,
                                42L,
                                99L,
                                false,
                                java.util.List.of("incorrect"),
                                false,
                                "feedback-1",
                                "sha256:digest"));

        mockMvc.perform(
                        post("/api/agent-runs/42/messages/99/feedback")
                                .cookie(new Cookie("foodmate_session", "session-token"))
                                .header("Idempotency-Key", "feedback-1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"helpful\":false,\"reason_codes\":[\"incorrect\"],\"comment\":\"不准确\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.feedback_id", is("100")))
                .andExpect(jsonPath("$.data.reason_codes[0]", is("incorrect")));
    }

    @Test
    void requiresSessionCookie() throws Exception {
        mockMvc.perform(
                        post("/api/agent-runs/42/messages/99/feedback")
                                .header("Idempotency-Key", "feedback-1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"helpful\":true}"))
                .andExpect(status().isUnauthorized());
    }
}
