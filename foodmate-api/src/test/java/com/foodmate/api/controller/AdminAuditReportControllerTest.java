package com.foodmate.api.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodmate.api.advice.GlobalExceptionHandler;
import com.foodmate.api.controller.account.AdminAuditReportController;
import com.foodmate.api.filter.TraceContextFilter;
import com.foodmate.application.account.service.AdminAuditReportService;
import com.foodmate.application.account.service.UserAccountService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AdminAuditReportControllerTest {
    private UserAccountService accounts;
    private AdminAuditReportService reports;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        accounts = Mockito.mock(UserAccountService.class);
        reports = Mockito.mock(AdminAuditReportService.class);
        mvc =
                MockMvcBuilders.standaloneSetup(new AdminAuditReportController(accounts, reports))
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .addFilters(new TraceContextFilter())
                        .build();
    }

    @Test
    void operatorCanReadSafeAggregateReport() throws Exception {
        when(accounts.requireSessionUser("operator-session")).thenReturn(user("operator"));
        when(reports.current())
                .thenReturn(
                        new AdminAuditReportService.Report(
                                Instant.parse("2026-08-23T01:00:00Z"),
                                15,
                                "attention",
                                List.of(
                                        new AdminAuditReportService.Check(
                                                "RUNTIME_DLQ",
                                                "attention",
                                                1,
                                                2,
                                                Instant.parse("2026-08-23T00:59:00Z"),
                                                List.of("DLQ_NEEDS_ATTENTION")))));

        mvc.perform(
                        get("/api/admin/audit-reports/current")
                                .cookie(
                                        new jakarta.servlet.http.Cookie(
                                                "foodmate_session", "operator-session")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status", is("attention")))
                .andExpect(jsonPath("$.data.stale_threshold_minutes", is(15)))
                .andExpect(jsonPath("$.data.checks[0].code", is("RUNTIME_DLQ")))
                .andExpect(jsonPath("$.data.checks[0].reason_codes[0]", is("DLQ_NEEDS_ATTENTION")))
                .andExpect(jsonPath("$.data.checks[0].raw_payload_json").doesNotExist());
    }

    @Test
    void ordinaryUserIsForbidden() throws Exception {
        when(accounts.requireSessionUser("user-session")).thenReturn(user("user"));

        mvc.perform(
                        get("/api/admin/audit-reports/current")
                                .cookie(
                                        new jakarta.servlet.http.Cookie(
                                                "foodmate_session", "user-session")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code", is("FORBIDDEN")));
    }

    private UserAccountService.UserRecord user(String role) {
        return new UserAccountService.UserRecord(
                2L, role, role + "@example.com", "hash", role, role, "active");
    }
}
