package com.foodmate.api.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodmate.api.advice.GlobalExceptionHandler;
import com.foodmate.api.controller.account.AdminManagementController;
import com.foodmate.api.filter.TraceContextFilter;
import com.foodmate.application.account.service.AdminManagementService;
import com.foodmate.application.account.service.AdminManagementService.AdminWriteCommand;
import com.foodmate.application.account.service.UserAccountService;
import com.foodmate.shared.account.enums.UserStatus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AdminManagementControllerTest {
    private UserAccountService accounts;
    private AdminManagementService management;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        accounts = Mockito.mock(UserAccountService.class);
        management = Mockito.mock(AdminManagementService.class);
        mvc =
                MockMvcBuilders.standaloneSetup(new AdminManagementController(accounts, management))
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .addFilters(new TraceContextFilter())
                        .build();
    }

    @Test
    void operatorCannotWrite() throws Exception {
        when(accounts.requireSessionUser("operator-session")).thenReturn(user("operator"));
        mvc.perform(
                        patch("/api/admin/users/9/status")
                                .cookie(
                                        new jakarta.servlet.http.Cookie(
                                                "foodmate_session", "operator-session"))
                                .contentType("application/json")
                                .header("Idempotency-Key", "operator-status-1")
                                .content("{\"status\":\"disabled\",\"revision\":1}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code", is("FORBIDDEN")));
    }

    @Test
    void adminCanWriteAndAudit() throws Exception {
        when(accounts.requireSessionUser("admin-session")).thenReturn(user("admin"));
        when(management.updateUserStatus(
                        anyLong(),
                        Mockito.any(UserStatus.class),
                        Mockito.any(AdminWriteCommand.class)))
                .thenReturn(new AdminManagementService.ManagementResult(true, "disabled", 0, 2));
        mvc.perform(
                        patch("/api/admin/users/9/status")
                                .cookie(
                                        new jakarta.servlet.http.Cookie(
                                                "foodmate_session", "admin-session"))
                                .contentType("application/json")
                                .header("Idempotency-Key", "admin-status-1")
                                .content("{\"status\":\"disabled\",\"revision\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.updated", is(true)))
                .andExpect(jsonPath("$.data.status", is("disabled")));
        Mockito.verify(management)
                .updateUserStatus(
                        anyLong(),
                        Mockito.any(UserStatus.class),
                        Mockito.any(AdminWriteCommand.class));
    }

    private UserAccountService.UserRecord user(String role) {
        return new UserAccountService.UserRecord(
                2L, role, role + "@example.com", "hash", role, role, "active");
    }
}
