package com.foodmate.api.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodmate.api.controller.account.AdminExportController;
import com.foodmate.application.account.service.AdminExportService;
import com.foodmate.application.account.service.UserAccountService;
import com.foodmate.shared.account.enums.UserRole;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AdminExportControllerTest {
    private UserAccountService accounts;
    private AdminExportService exports;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        accounts = Mockito.mock(UserAccountService.class);
        exports = Mockito.mock(AdminExportService.class);
        mvc =
                MockMvcBuilders.standaloneSetup(new AdminExportController(accounts, exports))
                        .setControllerAdvice(new com.foodmate.api.advice.GlobalExceptionHandler())
                        .build();
    }

    @Test
    void operatorCannotCreateExport() throws Exception {
        when(accounts.requireSessionUser("operator-session")).thenReturn(user("operator"));

        mvc.perform(
                        post("/api/admin/exports")
                                .cookie(new Cookie("foodmate_session", "operator-session"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("Idempotency-Key", "operator-export")
                                .content("{\"resource\":\"operation-audits\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code", is("FORBIDDEN")));
    }

    @Test
    void adminCanCreateExport() throws Exception {
        when(accounts.requireSessionUser("admin-session")).thenReturn(user("admin"));
        when(exports.request(
                        anyLong(),
                        any(UserRole.class),
                        any(AdminExportService.Request.class),
                        any()))
                .thenReturn(new AdminExportService.Created(42L));

        mvc.perform(
                        post("/api/admin/exports")
                                .cookie(new Cookie("foodmate_session", "admin-session"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("Idempotency-Key", "admin-export")
                                .content(
                                        "{\"resource\":\"operation-audits\",\"fields\":[\"action\",\"request_id\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.export_job_id", is(42)));
    }

    private UserAccountService.UserRecord user(String role) {
        return new UserAccountService.UserRecord(
                2L, role, role + "@example.com", "hash", role, role, "active");
    }
}
