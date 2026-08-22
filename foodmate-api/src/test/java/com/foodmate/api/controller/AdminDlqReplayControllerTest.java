package com.foodmate.api.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodmate.api.advice.GlobalExceptionHandler;
import com.foodmate.api.controller.account.AdminDlqReplayController;
import com.foodmate.api.filter.TraceContextFilter;
import com.foodmate.application.account.service.UserAccountService;
import com.foodmate.application.runtime.service.RuntimeDlqReplayService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AdminDlqReplayControllerTest {
    private UserAccountService accounts;
    private RuntimeDlqReplayService replay;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        accounts = Mockito.mock(UserAccountService.class);
        replay = Mockito.mock(RuntimeDlqReplayService.class);
        mvc =
                MockMvcBuilders.standaloneSetup(new AdminDlqReplayController(accounts, replay))
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .addFilters(new TraceContextFilter())
                        .build();
    }

    @Test
    void onlySuperadminCanCreateReplayRequest() throws Exception {
        when(accounts.requireSessionUser("superadmin-session")).thenReturn(user("superadmin"));
        when(replay.request(Mockito.eq(11L), Mockito.any(RuntimeDlqReplayService.Command.class)))
                .thenReturn(new RuntimeDlqReplayService.ReplayResult(901L, 11L, "queued", "mq-11"));

        mvc.perform(
                        post("/api/admin/dlq/11/replay")
                                .cookie(
                                        new jakarta.servlet.http.Cookie(
                                                "foodmate_session", "superadmin-session"))
                                .header("Idempotency-Key", "replay-11")
                                .contentType("application/json")
                                .content("{\"confirmed\":true,\"confirmationDigest\":\"digest\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.replay_id", is(901)))
                .andExpect(jsonPath("$.data.status", is("queued")))
                .andExpect(jsonPath("$.data.original_message_id", is("mq-11")))
                .andExpect(jsonPath("$.data.payload").doesNotExist());
    }

    @Test
    void adminIsForbiddenFromReplay() throws Exception {
        when(accounts.requireSessionUser("admin-session")).thenReturn(user("admin"));

        mvc.perform(
                        post("/api/admin/dlq/11/replay")
                                .cookie(
                                        new jakarta.servlet.http.Cookie(
                                                "foodmate_session", "admin-session"))
                                .header("Idempotency-Key", "replay-11")
                                .contentType("application/json")
                                .content("{\"confirmed\":true}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code", is("FORBIDDEN")));
        Mockito.verifyNoInteractions(replay);
    }

    @Test
    void ordinaryUserIsForbiddenFromReplay() throws Exception {
        when(accounts.requireSessionUser("user-session")).thenReturn(user("user"));

        mvc.perform(
                        post("/api/admin/dlq/11/replay")
                                .cookie(
                                        new jakarta.servlet.http.Cookie(
                                                "foodmate_session", "user-session"))
                                .contentType("application/json")
                                .content("{\"confirmed\":true}"))
                .andExpect(status().isForbidden());
    }

    private UserAccountService.UserRecord user(String role) {
        return new UserAccountService.UserRecord(
                7L, role, role + "@example.com", "hash", role, role, "active");
    }
}
