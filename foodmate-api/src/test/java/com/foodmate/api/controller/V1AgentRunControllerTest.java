package com.foodmate.api.controller;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodmate.api.advice.GlobalExceptionHandler;
import com.foodmate.api.controller.runtime.V1AgentRunController;
import com.foodmate.application.account.service.UserAccountService;
import com.foodmate.application.runtime.port.out.RuntimeRecoveryRepository;
import com.foodmate.application.runtime.service.BudgetExtensionService;
import com.foodmate.application.runtime.service.RuntimeCancellationService;
import com.foodmate.application.runtime.service.RuntimeRecoveryService;
import com.foodmate.application.runtime.service.V1RuntimeEventService;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class V1AgentRunControllerTest {
    private final UserAccountService accounts = mock(UserAccountService.class);
    private final V1RuntimeEventService events = mock(V1RuntimeEventService.class);
    private final RuntimeCancellationService cancellations = mock(RuntimeCancellationService.class);
    private final BudgetExtensionService budgets = mock(BudgetExtensionService.class);
    private final RuntimeRecoveryService recovery = mock(RuntimeRecoveryService.class);
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
                MockMvcBuilders.standaloneSetup(
                                new V1AgentRunController(
                                        accounts, events, cancellations, budgets, recovery))
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .build();
    }

    @Test
    void recoveryEndpointAuthenticatesUserAndPassesCheckpointFacts() throws Exception {
        when(recovery.recover(any()))
                .thenReturn(new RuntimeRecoveryService.RecoveryResult("1", "d-new", 2, "queued"));

        mockMvc.perform(
                        post("/api/agent-runs/1/recover")
                                .cookie(new Cookie("foodmate_session", "session-token"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"checkpoint_version\":4,"
                                                + "\"checkpoint_digest\":\"sha256:checkpoint\","
                                                + "\"completed_invocation_ids\":[\"inv-1\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.run_id", is("1")))
                .andExpect(jsonPath("$.data.dispatch_id", is("d-new")))
                .andExpect(jsonPath("$.data.attempt", is(2)));

        ArgumentCaptor<RuntimeRecoveryRepository.RecoveryRequest> request =
                ArgumentCaptor.forClass(RuntimeRecoveryRepository.RecoveryRequest.class);
        verify(recovery).recover(request.capture());
        assertEquals(7L, request.getValue().userId());
        assertEquals(1L, request.getValue().runId());
        assertEquals(4, request.getValue().checkpointVersion());
        assertEquals("sha256:checkpoint", request.getValue().checkpointDigest());
        assertEquals(List.of("inv-1"), request.getValue().completedInvocationIds());
    }

    @Test
    void recoveryRequiresSessionCookie() throws Exception {
        mockMvc.perform(
                        post("/api/agent-runs/1/recover")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"checkpoint_version\":1,"
                                                + "\"checkpoint_digest\":\"sha256:x\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success", is(false)));
    }

    @Test
    void persistedCheckpointRecoveryUsesAuthenticatedUser() throws Exception {
        when(recovery.recoverFromPersistedCheckpoint(7L, 1L))
                .thenReturn(
                        new RuntimeRecoveryService.RecoveryResult("1", "d-recovered", 2, "queued"));

        mockMvc.perform(
                        post("/api/agent-runs/1/recover-from-checkpoint")
                                .cookie(new Cookie("foodmate_session", "session-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dispatch_id", is("d-recovered")));

        verify(recovery).recoverFromPersistedCheckpoint(7L, 1L);
    }
}
