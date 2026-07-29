package com.foodmate.api.controller;

import com.foodmate.application.account.UserAccountService;
import com.foodmate.application.runtime.RuntimeCancellationService;
import com.foodmate.application.runtime.V1RuntimeEventService;
import com.foodmate.shared.api.ApiResponse;
import com.foodmate.shared.trace.TraceContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent-runs")
public class V1AgentRunController extends AuthenticatedControllerSupport {
    private final V1RuntimeEventService events;
    private final RuntimeCancellationService cancellations;
    private final com.foodmate.application.runtime.BudgetExtensionService budgets;

    public V1AgentRunController(
            UserAccountService accounts,
            V1RuntimeEventService events,
            RuntimeCancellationService cancellations,
            com.foodmate.application.runtime.BudgetExtensionService budgets) {
        super(accounts);
        this.events = events;
        this.cancellations = cancellations;
        this.budgets = budgets;
    }

    @GetMapping("/{runId}")
    public ApiResponse<RunView> status(@PathVariable String runId, HttpServletRequest request) {
        events.requireRunOwner(runId, user(request).userId());
        var history = events.events(runId);
        return ApiResponse.success(
                new RunView(runId, events.status(runId), history.size()),
                TraceContextHolder.currentOrNew());
    }

    @PostMapping("/{runId}/cancel")
    public ApiResponse<RuntimeCancellationService.CancelResult> cancel(
            @PathVariable String runId,
            HttpServletRequest request,
            @Valid @RequestBody(required = false) CancelRequest body) {
        String reason =
                body == null || body.reason() == null || body.reason().isBlank()
                        ? "user_requested"
                        : body.reason();
        return ApiResponse.success(
                cancellations.request(user(request).userId(), runId, reason),
                TraceContextHolder.currentOrNew());
    }

    @PostMapping("/{runId}/budget-extensions")
    public ApiResponse<com.foodmate.application.runtime.BudgetExtensionService.ExtensionResult>
            extendBudget(
                    @PathVariable String runId,
                    HttpServletRequest request,
                    @Valid @RequestBody BudgetExtensionRequest body) {
        return ApiResponse.success(
                budgets.confirm(
                        user(request).userId(),
                        Long.parseLong(runId),
                        body.additionalTokens(),
                        body.additionalCostCny(),
                        body.confirmationDigest()),
                TraceContextHolder.currentOrNew());
    }

    public record RunView(String runId, String status, int acceptedEventCount) {}

    public record CancelRequest(@NotBlank String reason) {}

    public record BudgetExtensionRequest(
            @Min(1) int additionalTokens,
            @DecimalMin("0.0001") BigDecimal additionalCostCny,
            @NotBlank String confirmationDigest) {}
}
