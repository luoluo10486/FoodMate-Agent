package com.foodmate.api.controller.account;

import com.foodmate.api.request.account.ModelBudgetRequest;
import com.foodmate.api.request.account.ModelPriceRequest;
import com.foodmate.api.request.account.ModelRouteRequest;
import com.foodmate.api.request.account.ModelStatusRequest;
import com.foodmate.api.response.account.ModelGovernanceMutationResponse;
import com.foodmate.api.response.account.ModelGovernanceResponse;
import com.foodmate.application.account.service.AdminManagementService.AdminWriteCommand;
import com.foodmate.application.account.service.UserAccountService;
import com.foodmate.application.runtime.service.ModelGovernanceAdminService;
import com.foodmate.shared.account.enums.UserRole;
import com.foodmate.shared.api.ApiResponse;
import com.foodmate.shared.trace.TraceContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.Instant;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Safe model governance API; write authorization is also enforced by application. */
@RestController
@RequestMapping("/api/admin/model-governance")
public class ModelGovernanceController extends AuthenticatedControllerSupport {
    private final ModelGovernanceAdminService governance;

    public ModelGovernanceController(
            UserAccountService accounts, ModelGovernanceAdminService governance) {
        super(accounts);
        this.governance = governance;
    }

    @GetMapping
    public ApiResponse<ModelGovernanceResponse> view(
            HttpServletRequest request,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {
        requireAnyRole(request, UserRole.OPERATOR, UserRole.ADMIN, UserRole.SUPERADMIN);
        return ok(
                ModelGovernanceResponse.from(
                        governance.view(new ModelGovernanceAdminService.UsageQuery(from, to))));
    }

    @PatchMapping("/providers/{providerCode}/status")
    public ApiResponse<ModelGovernanceMutationResponse> providerStatus(
            @PathVariable String providerCode,
            @Valid @RequestBody ModelStatusRequest body,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest request) {
        var operator = requireAnyRole(request, UserRole.SUPERADMIN);
        var result =
                governance.updateProviderStatus(
                        providerCode,
                        body.status(),
                        command(
                                operator,
                                idempotencyKey,
                                body.revision(),
                                body.confirmed(),
                                body.confirmationDigest()));
        return ok(
                new ModelGovernanceMutationResponse(
                        result.changed(),
                        result.resourceId(),
                        result.version(),
                        result.revision()));
    }

    @PatchMapping("/models/{id}/status")
    public ApiResponse<ModelGovernanceMutationResponse> modelStatus(
            @PathVariable long id,
            @Valid @RequestBody ModelStatusRequest body,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest request) {
        var operator = requireAnyRole(request, UserRole.SUPERADMIN);
        var result =
                governance.updateModelStatus(
                        id,
                        body.status(),
                        command(
                                operator,
                                idempotencyKey,
                                body.revision(),
                                body.confirmed(),
                                body.confirmationDigest()));
        return ok(
                new ModelGovernanceMutationResponse(
                        result.changed(),
                        result.resourceId(),
                        result.version(),
                        result.revision()));
    }

    @PutMapping("/routes/{id}")
    public ApiResponse<ModelGovernanceMutationResponse> route(
            @PathVariable long id,
            @Valid @RequestBody ModelRouteRequest body,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest request) {
        var operator = requireAnyRole(request, UserRole.SUPERADMIN);
        var result =
                governance.updateRoute(
                        new ModelGovernanceAdminService.RouteCommand(
                                id,
                                body.providerCode(),
                                body.modelName(),
                                body.fallbackProviderCode(),
                                body.fallbackModelName(),
                                body.priority(),
                                body.routeVersion(),
                                body.priceVersion(),
                                body.budgetPolicyVersion(),
                                body.maxCost(),
                                body.maxLatencyMs(),
                                body.status(),
                                body.revision()),
                        command(
                                operator,
                                idempotencyKey,
                                body.revision(),
                                body.confirmed(),
                                body.confirmationDigest()));
        return ok(
                new ModelGovernanceMutationResponse(
                        result.changed(),
                        result.resourceId(),
                        result.version(),
                        result.revision()));
    }

    @PostMapping("/prices")
    public ApiResponse<ModelGovernanceMutationResponse> price(
            @Valid @RequestBody ModelPriceRequest body,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest request) {
        var operator = requireAnyRole(request, UserRole.SUPERADMIN);
        var result =
                governance.createPrice(
                        new ModelGovernanceAdminService.PriceCommand(
                                body.providerCode(),
                                body.modelName(),
                                body.priceVersion(),
                                body.inputPricePerMillion(),
                                body.outputPricePerMillion(),
                                body.currency(),
                                body.effectiveAt()),
                        command(
                                operator,
                                idempotencyKey,
                                body.revision(),
                                body.confirmed(),
                                body.confirmationDigest()));
        return ok(
                new ModelGovernanceMutationResponse(
                        result.changed(),
                        result.resourceId(),
                        result.version(),
                        result.revision()));
    }

    @PostMapping("/budgets")
    public ApiResponse<ModelGovernanceMutationResponse> budget(
            @Valid @RequestBody ModelBudgetRequest body,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest request) {
        var operator = requireAnyRole(request, UserRole.SUPERADMIN);
        var result =
                governance.createBudget(
                        new ModelGovernanceAdminService.BudgetCommand(
                                body.policyKey(),
                                body.scene(),
                                body.scopeType(),
                                body.maxTotalTokens(),
                                body.maxCostCny(),
                                body.maxModelCalls(),
                                body.maxStepRetries(),
                                body.windowType(),
                                body.policyVersion()),
                        command(
                                operator,
                                idempotencyKey,
                                body.revision(),
                                body.confirmed(),
                                body.confirmationDigest()));
        return ok(
                new ModelGovernanceMutationResponse(
                        result.changed(),
                        result.resourceId(),
                        result.version(),
                        result.revision()));
    }

    private AdminWriteCommand command(
            UserAccountService.UserRecord operator,
            String idempotencyKey,
            long revision,
            boolean confirmed,
            String confirmationDigest) {
        return new AdminWriteCommand(
                operator.userId(),
                UserRole.fromCode(operator.role()),
                TraceContextHolder.currentOrNew().traceId(),
                idempotencyKey,
                revision,
                confirmed,
                confirmationDigest);
    }

    private <T> ApiResponse<T> ok(T value) {
        return ApiResponse.success(value, TraceContextHolder.currentOrNew());
    }
}
