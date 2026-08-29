package com.foodmate.api.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodmate.api.advice.GlobalExceptionHandler;
import com.foodmate.api.controller.account.AdminOperationalQueryController;
import com.foodmate.api.filter.TraceContextFilter;
import com.foodmate.application.account.service.AdminOperationalQueryService;
import com.foodmate.application.account.service.UserAccountService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AdminOperationalQueryControllerTest {
    private UserAccountService accounts;
    private AdminOperationalQueryService queries;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        accounts = Mockito.mock(UserAccountService.class);
        queries = Mockito.mock(AdminOperationalQueryService.class);
        mvc =
                MockMvcBuilders.standaloneSetup(
                                new AdminOperationalQueryController(accounts, queries))
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .addFilters(new TraceContextFilter())
                        .build();
    }

    @Test
    void operatorCanReadPagedOperationalSummary() throws Exception {
        when(accounts.requireSessionUser("operator-session")).thenReturn(user("operator"));
        doReturn(
                        new AdminOperationalQueryService.Page<
                                AdminOperationalQueryService.SqlAudit>(
                                List.of(
                                        new AdminOperationalQueryService.SqlAudit(
                                                8L,
                                                3L,
                                                "hash",
                                                "executed",
                                                "trace",
                                                12L,
                                                2L,
                                                null,
                                                null)),
                                1,
                                2,
                                100))
                .when(queries)
                .query(eq("sql-audits"), any(AdminOperationalQueryService.Request.class));

        mvc.perform(
                        get("/api/admin/queries/sql-audits")
                                .param("page", "2")
                                .param("size", "200")
                                .cookie(
                                        new jakarta.servlet.http.Cookie(
                                                "foodmate_session", "operator-session")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resource", is("sql-audits")))
                .andExpect(jsonPath("$.data.page", is(2)))
                .andExpect(jsonPath("$.data.size", is(100)))
                .andExpect(jsonPath("$.data.items[0].query_hash", is("hash")))
                .andExpect(jsonPath("$.data.items[0].statement").doesNotExist());

        verify(queries).query(eq("sql-audits"), any(AdminOperationalQueryService.Request.class));
    }

    @Test
    void ordinaryUserIsForbidden() throws Exception {
        when(accounts.requireSessionUser("user-session")).thenReturn(user("user"));

        mvc.perform(
                        get("/api/admin/queries/runs")
                                .cookie(
                                        new jakarta.servlet.http.Cookie(
                                                "foodmate_session", "user-session")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code", is("FORBIDDEN")));
    }

    @Test
    void operatorCanReadDlqSummaryWithoutPayload() throws Exception {
        when(accounts.requireSessionUser("operator-session")).thenReturn(user("operator"));
        doReturn(
                        new AdminOperationalQueryService.Page<AdminOperationalQueryService.Dlq>(
                                List.of(
                                        new AdminOperationalQueryService.Dlq(
                                                21L,
                                                "foodmate-java-agent-event-v1",
                                                "foodmate-agent-event-v1",
                                                "mq-21",
                                                "42",
                                                "dispatch-42",
                                                "event-42",
                                                2,
                                                8,
                                                "RUNTIME_MESSAGE_DEAD_LETTERED",
                                                "needs_attention",
                                                null,
                                                null)),
                                1,
                                1,
                                20))
                .when(queries)
                .query(eq("dlq"), any(AdminOperationalQueryService.Request.class));

        mvc.perform(
                        get("/api/admin/queries/dlq")
                                .cookie(
                                        new jakarta.servlet.http.Cookie(
                                                "foodmate_session", "operator-session")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resource", is("dlq")))
                .andExpect(jsonPath("$.data.items[0].message_id", is("mq-21")))
                .andExpect(jsonPath("$.data.items[0].reconciliation_state", is("needs_attention")))
                .andExpect(jsonPath("$.data.items[0].raw_payload_json").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].last_error").doesNotExist());

        verify(queries).query(eq("dlq"), any(AdminOperationalQueryService.Request.class));
    }

    @Test
    void operatorCanReadTraceSummary() throws Exception {
        when(accounts.requireSessionUser("operator-session")).thenReturn(user("operator"));
        doReturn(
                        new AdminOperationalQueryService.Page<AdminOperationalQueryService.Trace>(
                                List.of(
                                        new AdminOperationalQueryService.Trace(
                                                "trace-1",
                                                42L,
                                                "java.control-plane -> python.agent-runtime",
                                                "completed",
                                                java.time.Instant.parse("2026-08-28T00:00:00Z"),
                                                new java.math.BigDecimal("10.0"),
                                                2L,
                                                "foodmate-java",
                                                null)),
                                1,
                                1,
                                20))
                .when(queries)
                .query(eq("traces"), any(AdminOperationalQueryService.Request.class));

        mvc.perform(
                        get("/api/admin/queries/traces")
                                .cookie(
                                        new jakarta.servlet.http.Cookie(
                                                "foodmate_session", "operator-session")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resource", is("traces")))
                .andExpect(jsonPath("$.data.items[0].trace_id", is("trace-1")))
                .andExpect(jsonPath("$.data.items[0].span_count", is(2)))
                .andExpect(jsonPath("$.data.items[0].root_service", is("foodmate-java")))
                .andExpect(jsonPath("$.data.items[0].raw_payload_json").doesNotExist());

        verify(queries).query(eq("traces"), any(AdminOperationalQueryService.Request.class));
    }

    @Test
    void operatorCanReadRedactedTraceDetail() throws Exception {
        when(accounts.requireSessionUser("operator-session")).thenReturn(user("operator"));
        when(queries.traceDetail("trace-detail"))
                .thenReturn(
                        new AdminOperationalQueryService.TraceDetail(
                                new AdminOperationalQueryService.Trace(
                                        "trace-detail",
                                        42L,
                                        "java.control-plane -> python.agent-runtime",
                                        "completed",
                                        java.time.Instant.parse("2026-08-28T00:00:00Z"),
                                        new java.math.BigDecimal("10.0"),
                                        1L,
                                        "foodmate-java",
                                        null),
                                List.of(
                                        new AdminOperationalQueryService.TraceSpan(
                                                "event-1",
                                                "runtime_event",
                                                "run.completed",
                                                "python.agent-runtime",
                                                "success",
                                                java.time.Instant.parse("2026-08-28T00:00:00Z"),
                                                java.time.Instant.parse("2026-08-28T00:00:00.010Z"),
                                                new java.math.BigDecimal("10"),
                                                null,
                                                4L))));

        mvc.perform(
                        get("/api/admin/queries/traces/trace-detail")
                                .cookie(
                                        new jakarta.servlet.http.Cookie(
                                                "foodmate_session", "operator-session")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.trace_id", is("trace-detail")))
                .andExpect(jsonPath("$.data.spans[0].span_type", is("runtime_event")))
                .andExpect(jsonPath("$.data.spans[0].name", is("run.completed")))
                .andExpect(jsonPath("$.data.spans[0].payload_json").doesNotExist());

        verify(queries).traceDetail("trace-detail");
    }

    private UserAccountService.UserRecord user(String role) {
        return new UserAccountService.UserRecord(
                2L, role, role + "@example.com", "hash", role, role, "active");
    }
}
