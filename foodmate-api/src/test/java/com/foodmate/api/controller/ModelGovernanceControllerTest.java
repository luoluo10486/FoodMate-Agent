package com.foodmate.api.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodmate.api.advice.GlobalExceptionHandler;
import com.foodmate.api.controller.account.ModelGovernanceController;
import com.foodmate.api.filter.TraceContextFilter;
import com.foodmate.application.account.service.AdminManagementService.AdminWriteCommand;
import com.foodmate.application.account.service.UserAccountService;
import com.foodmate.application.runtime.service.ModelGovernanceAdminService;
import com.foodmate.application.runtime.service.impl.ModelGovernanceAdminServiceImpl;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ModelGovernanceControllerTest {
    private UserAccountService accounts;
    private ModelGovernanceAdminService governance;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        accounts = Mockito.mock(UserAccountService.class);
        governance = Mockito.mock(ModelGovernanceAdminService.class);
        mvc =
                MockMvcBuilders.standaloneSetup(new ModelGovernanceController(accounts, governance))
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .addFilters(new TraceContextFilter())
                        .build();
    }

    @Test
    void operatorCanReadSafeGovernanceView() throws Exception {
        when(accounts.requireSessionUser("operator-session")).thenReturn(user("operator"));
        when(governance.view(any(ModelGovernanceAdminService.UsageQuery.class)))
                .thenReturn(
                        new ModelGovernanceAdminService.GovernanceView(
                                List.of(
                                        new ModelGovernanceAdminService.ProviderView(
                                                11L,
                                                "openai",
                                                "OpenAI",
                                                "active",
                                                "openai.endpoint",
                                                true,
                                                "sha256:fingerprint",
                                                1L)),
                                List.of(),
                                List.of(),
                                List.of(),
                                List.of(),
                                List.of()));

        mvc.perform(
                        get("/api/admin/model-governance")
                                .cookie(
                                        new jakarta.servlet.http.Cookie(
                                                "foodmate_session", "operator-session")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.providers[0].provider_code", is("openai")))
                .andExpect(jsonPath("$.data.providers[0].configured", is(true)))
                .andExpect(jsonPath("$.data.providers[0].fingerprint", is("sha256:fingerprint")))
                .andExpect(jsonPath("$.data.providers[0].api_key").doesNotExist());
    }

    @Test
    void ordinaryUserCannotWriteProviderStatus() throws Exception {
        when(accounts.requireSessionUser("user-session")).thenReturn(user("user"));

        mvc.perform(
                        patch("/api/admin/model-governance/providers/openai/status")
                                .cookie(
                                        new jakarta.servlet.http.Cookie(
                                                "foodmate_session", "user-session"))
                                .header("Idempotency-Key", "provider-status-user")
                                .contentType("application/json")
                                .content("{\"status\":\"disabled\",\"revision\":1}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code", is("FORBIDDEN")));
    }

    @Test
    void superadminWritePassesConfirmationAndIdempotencyContext() throws Exception {
        when(accounts.requireSessionUser("superadmin-session")).thenReturn(user("superadmin"));
        when(governance.updateProviderStatus(
                        Mockito.eq("openai"), Mockito.eq("disabled"), any(AdminWriteCommand.class)))
                .thenReturn(
                        new ModelGovernanceAdminService.MutationResult(true, 11L, "disabled", 2L));
        String confirmation =
                ModelGovernanceAdminServiceImpl.confirmationDigest(
                        "model.provider.status.update", "openai", "disabled", 1L);

        mvc.perform(
                        patch("/api/admin/model-governance/providers/openai/status")
                                .cookie(
                                        new jakarta.servlet.http.Cookie(
                                                "foodmate_session", "superadmin-session"))
                                .header("Idempotency-Key", "provider-status-1")
                                .contentType("application/json")
                                .content(
                                        "{\"status\":\"disabled\",\"revision\":1,\"confirmed\":true,\"confirmationDigest\":\""
                                                + confirmation
                                                + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.changed", is(true)))
                .andExpect(jsonPath("$.data.resource_id", is(11)))
                .andExpect(jsonPath("$.data.revision", is(2)));

        ArgumentCaptor<AdminWriteCommand> command =
                ArgumentCaptor.forClass(AdminWriteCommand.class);
        verify(governance)
                .updateProviderStatus(
                        Mockito.eq("openai"), Mockito.eq("disabled"), command.capture());
        org.junit.jupiter.api.Assertions.assertEquals(
                "provider-status-1", command.getValue().idempotencyKey());
        org.junit.jupiter.api.Assertions.assertEquals(1L, command.getValue().revision());
        org.junit.jupiter.api.Assertions.assertTrue(command.getValue().confirmed());
    }

    private static UserAccountService.UserRecord user(String role) {
        return new UserAccountService.UserRecord(
                7L, role, role + "@example.com", "hash", role, role, "active");
    }
}
