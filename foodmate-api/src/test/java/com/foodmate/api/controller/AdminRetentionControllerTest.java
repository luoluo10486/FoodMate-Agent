package com.foodmate.api.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodmate.api.advice.GlobalExceptionHandler;
import com.foodmate.api.controller.account.AdminRetentionController;
import com.foodmate.api.filter.TraceContextFilter;
import com.foodmate.application.account.service.UserAccountService;
import com.foodmate.application.retention.service.DataRetentionService;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AdminRetentionControllerTest {
    private UserAccountService accounts;
    private DataRetentionService retention;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        accounts = Mockito.mock(UserAccountService.class);
        retention = Mockito.mock(DataRetentionService.class);
        mvc =
                MockMvcBuilders.standaloneSetup(new AdminRetentionController(accounts, retention))
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .addFilters(new TraceContextFilter())
                        .build();
    }

    @Test
    void adminCanCreatePurgePlanWithoutExposingInternalTasks() throws Exception {
        when(accounts.requireSessionUser("admin-session")).thenReturn(user("admin"));
        when(retention.requestPurge(Mockito.any(DataRetentionService.PurgeCommand.class)))
                .thenReturn(
                        new DataRetentionService.PurgeResult(
                                901L,
                                "requested",
                                "knowledge_document",
                                42L,
                                Instant.parse("2026-08-22T00:00:00Z"),
                                0));

        mvc.perform(
                        post("/api/admin/data-retention/purge-requests")
                                .cookie(
                                        new jakarta.servlet.http.Cookie(
                                                "foodmate_session", "admin-session"))
                                .header("Idempotency-Key", "purge-42")
                                .contentType("application/json")
                                .content(
                                        "{\"resource_type\":\"knowledge_document\",\"resource_id\":42,\"confirmed\":true,\"confirmation_digest\":\"digest\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.request_id", is(901)))
                .andExpect(jsonPath("$.data.status", is("requested")))
                .andExpect(jsonPath("$.data.task_count", is(0)))
                .andExpect(jsonPath("$.data.target_ref").doesNotExist());
    }

    @Test
    void adminCannotApprovePurgePlan() throws Exception {
        when(accounts.requireSessionUser("admin-session")).thenReturn(user("admin"));

        mvc.perform(
                        post("/api/admin/data-retention/purge-requests/901/approve")
                                .cookie(
                                        new jakarta.servlet.http.Cookie(
                                                "foodmate_session", "admin-session"))
                                .header("Idempotency-Key", "approve-901")
                                .contentType("application/json")
                                .content("{\"confirmed\":true}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code", is("FORBIDDEN")));
        Mockito.verifyNoInteractions(retention);
    }

    @Test
    void operatorCanReadSafePurgePreflight() throws Exception {
        when(accounts.requireSessionUser("operator-session")).thenReturn(user("operator"));
        when(retention.getPreflight(901L))
                .thenReturn(
                        new DataRetentionService.PurgePreflight(
                                901L,
                                "approved",
                                "knowledge_document",
                                42L,
                                true,
                                false,
                                true,
                                true,
                                true,
                                true,
                                false,
                                java.util.List.of(
                                        new DataRetentionService.PurgeTaskState(
                                                "database", "pending", 0, null)),
                                java.util.List.of("RETENTION_HARD_DELETE_DISABLED")));

        mvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(
                                        "/api/admin/data-retention/purge-requests/901/preflight")
                                .cookie(
                                        new jakarta.servlet.http.Cookie(
                                                "foodmate_session", "operator-session")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ready_to_execute", is(false)))
                .andExpect(jsonPath("$.data.blockers[0]", is("RETENTION_HARD_DELETE_DISABLED")))
                .andExpect(jsonPath("$.data.tasks[0].task_type", is("database")))
                .andExpect(jsonPath("$.data.target_ref").doesNotExist());
    }

    @Test
    void onlySuperadminCanReleaseHold() throws Exception {
        when(accounts.requireSessionUser("superadmin-session")).thenReturn(user("superadmin"));
        when(retention.releaseHold(
                        Mockito.eq(88L), Mockito.any(DataRetentionService.ReleaseCommand.class)))
                .thenReturn(
                        new DataRetentionService.HoldResult(
                                88L, "released", "knowledge_document", 42L, "legal_case"));

        mvc.perform(
                        post("/api/admin/data-retention/holds/88/release")
                                .cookie(
                                        new jakarta.servlet.http.Cookie(
                                                "foodmate_session", "superadmin-session"))
                                .header("Idempotency-Key", "release-88")
                                .contentType("application/json")
                                .content("{\"confirmed\":true,\"confirmation_digest\":\"digest\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.hold_id", is(88)))
                .andExpect(jsonPath("$.data.status", is("released")));
    }

    private UserAccountService.UserRecord user(String role) {
        return new UserAccountService.UserRecord(
                role.equals("superadmin") ? 8L : 7L,
                role,
                role + "@example.com",
                "hash",
                role,
                role,
                "active");
    }
}
