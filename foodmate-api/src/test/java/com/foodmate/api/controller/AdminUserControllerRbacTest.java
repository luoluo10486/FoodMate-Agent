package com.foodmate.api.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodmate.application.account.UserAccountService;
import com.foodmate.api.advice.GlobalExceptionHandler;
import com.foodmate.api.filter.TraceContextFilter;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.mockito.Mockito;

class AdminUserControllerRbacTest {
    private UserAccountService accounts;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        accounts = Mockito.mock(UserAccountService.class);
        mvc = MockMvcBuilders.standaloneSetup(new AdminUserController(accounts))
                .setControllerAdvice(new GlobalExceptionHandler()).addFilters(new TraceContextFilter()).build();
        when(accounts.listUsersForAdmin()).thenReturn(List.of(new UserAccountService.AdminUserView(1L, "safe", "safe@example.com", "Safe", "user", "active")));
    }

    @Test
    void ordinaryUserIsForbidden() throws Exception {
        when(accounts.requireSessionUser("user-session")).thenReturn(new UserAccountService.UserRecord(2L, "u", "u@example.com", "hash", "U", "user", "active"));
        mvc.perform(get("/api/admin/users").cookie(new jakarta.servlet.http.Cookie("foodmate_session", "user-session")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.error.code", is("FORBIDDEN")));
    }

    @Test
    void operatorAdminAndSuperadminCanReadWithoutSensitiveFields() throws Exception {
        for (String role : List.of("operator", "admin", "superadmin")) {
            when(accounts.requireSessionUser(role + "-session")).thenReturn(new UserAccountService.UserRecord(2L, role, role + "@example.com", "hash", role, role, "active"));
            mvc.perform(get("/api/admin/users").cookie(new jakarta.servlet.http.Cookie("foodmate_session", role + "-session")))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].username", is("safe")))
                    .andExpect(jsonPath("$.data[0].password_hash").doesNotExist())
                    .andExpect(jsonPath("$.data[0].csrf_token_hash").doesNotExist());
        }
    }
}
