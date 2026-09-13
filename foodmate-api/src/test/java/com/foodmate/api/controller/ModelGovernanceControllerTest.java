package com.foodmate.api.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodmate.api.advice.GlobalExceptionHandler;
import com.foodmate.api.controller.account.ModelGovernanceController;
import com.foodmate.api.filter.TraceContextFilter;
import com.foodmate.application.account.service.AdminManagementService.AdminWriteCommand;
import com.foodmate.application.account.service.UserAccountService;
import com.foodmate.application.runtime.service.ModelGovernanceAdminService;
import com.foodmate.application.runtime.service.impl.ModelGovernanceAdminServiceImpl;
import java.math.BigDecimal;
import java.time.Instant;
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

    @Test
    void superadminCanCreateModelPrice() throws Exception {
        when(accounts.requireSessionUser("superadmin-session")).thenReturn(user("superadmin"));
        when(governance.createPrice(
                        any(ModelGovernanceAdminService.PriceCommand.class),
                        any(AdminWriteCommand.class)))
                .thenReturn(
                        new ModelGovernanceAdminService.MutationResult(true, 21L, "price-v2", 1L));

        mvc.perform(
                        post("/api/admin/model-governance/prices")
                                .cookie(
                                        new jakarta.servlet.http.Cookie(
                                                "foodmate_session", "superadmin-session"))
                                .header("Idempotency-Key", "price-create-1")
                                .contentType("application/json")
                                .content(
                                        "{"
                                                + "\"providerCode\":\"cloud_primary\","
                                                + "\"modelName\":\"Qwen3\","
                                                + "\"priceVersion\":\"price-v2\","
                                                + "\"inputPricePerMillion\":1.2,"
                                                + "\"outputPricePerMillion\":2.4,"
                                                + "\"currency\":\"CNY\","
                                                + "\"effectiveAt\":\"2026-09-14T02:30:00Z\","
                                                + "\"revision\":1,"
                                                + "\"confirmed\":true,"
                                                + "\"confirmationDigest\":\"digest\""
                                                + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resource_id", is(21)))
                .andExpect(jsonPath("$.data.version", is("price-v2")));

        ArgumentCaptor<ModelGovernanceAdminService.PriceCommand> price =
                ArgumentCaptor.forClass(ModelGovernanceAdminService.PriceCommand.class);
        ArgumentCaptor<AdminWriteCommand> command =
                ArgumentCaptor.forClass(AdminWriteCommand.class);
        verify(governance).createPrice(price.capture(), command.capture());
        org.junit.jupiter.api.Assertions.assertEquals(
                "cloud_primary", price.getValue().providerCode());
        org.junit.jupiter.api.Assertions.assertEquals("Qwen3", price.getValue().modelName());
        org.junit.jupiter.api.Assertions.assertEquals(
                new BigDecimal("1.2"), price.getValue().inputPricePerMillion());
        org.junit.jupiter.api.Assertions.assertEquals(
                Instant.parse("2026-09-14T02:30:00Z"), price.getValue().effectiveAt());
        org.junit.jupiter.api.Assertions.assertEquals(
                "price-create-1", command.getValue().idempotencyKey());
        org.junit.jupiter.api.Assertions.assertEquals(1L, command.getValue().revision());
        org.junit.jupiter.api.Assertions.assertTrue(command.getValue().confirmed());
    }

    @Test
    void superadminCanCreateModelBudget() throws Exception {
        when(accounts.requireSessionUser("superadmin-session")).thenReturn(user("superadmin"));
        when(governance.createBudget(
                        any(ModelGovernanceAdminService.BudgetCommand.class),
                        any(AdminWriteCommand.class)))
                .thenReturn(
                        new ModelGovernanceAdminService.MutationResult(true, 22L, "budget-v2", 1L));

        mvc.perform(
                        post("/api/admin/model-governance/budgets")
                                .cookie(
                                        new jakarta.servlet.http.Cookie(
                                                "foodmate_session", "superadmin-session"))
                                .header("Idempotency-Key", "budget-create-1")
                                .contentType("application/json")
                                .content(
                                        "{"
                                                + "\"policyKey\":\"agent-default\","
                                                + "\"scene\":\"chat\","
                                                + "\"scopeType\":\"global\","
                                                + "\"maxTotalTokens\":50000,"
                                                + "\"maxCostCny\":10.00,"
                                                + "\"maxModelCalls\":10,"
                                                + "\"maxStepRetries\":2,"
                                                + "\"windowType\":\"run\","
                                                + "\"policyVersion\":\"budget-v2\","
                                                + "\"revision\":1,"
                                                + "\"confirmed\":true,"
                                                + "\"confirmationDigest\":\"digest\""
                                                + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.resource_id", is(22)))
                .andExpect(jsonPath("$.data.version", is("budget-v2")));

        ArgumentCaptor<ModelGovernanceAdminService.BudgetCommand> budget =
                ArgumentCaptor.forClass(ModelGovernanceAdminService.BudgetCommand.class);
        ArgumentCaptor<AdminWriteCommand> command =
                ArgumentCaptor.forClass(AdminWriteCommand.class);
        verify(governance).createBudget(budget.capture(), command.capture());
        org.junit.jupiter.api.Assertions.assertEquals(
                "agent-default", budget.getValue().policyKey());
        org.junit.jupiter.api.Assertions.assertEquals("global", budget.getValue().scopeType());
        org.junit.jupiter.api.Assertions.assertEquals(50000, budget.getValue().maxTotalTokens());
        org.junit.jupiter.api.Assertions.assertEquals(
                new BigDecimal("10.00"), budget.getValue().maxCostCny());
        org.junit.jupiter.api.Assertions.assertEquals(
                "budget-create-1", command.getValue().idempotencyKey());
        org.junit.jupiter.api.Assertions.assertEquals(1L, command.getValue().revision());
        org.junit.jupiter.api.Assertions.assertTrue(command.getValue().confirmed());
    }

    private static UserAccountService.UserRecord user(String role) {
        return new UserAccountService.UserRecord(
                7L, role, role + "@example.com", "hash", role, role, "active");
    }
}
