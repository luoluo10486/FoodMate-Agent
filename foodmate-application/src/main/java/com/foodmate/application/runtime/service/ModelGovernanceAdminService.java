package com.foodmate.application.runtime.service;

import com.foodmate.application.account.service.AdminManagementService.AdminWriteCommand;
import com.foodmate.application.runtime.port.out.ModelGovernanceAdminRepository.BudgetRow;
import com.foodmate.application.runtime.port.out.ModelGovernanceAdminRepository.PriceRow;
import java.math.BigDecimal;
import java.time.Instant;

/** Model governance use cases with superadmin, confirmation, revision and audit enforcement. */
public interface ModelGovernanceAdminService {
    GovernanceView view(UsageQuery query);

    record UsageQuery(Instant from, Instant to) {}

    MutationResult updateProviderStatus(
            String providerCode, String status, AdminWriteCommand command);

    MutationResult updateModelStatus(long modelId, String status, AdminWriteCommand command);

    MutationResult updateRoute(RouteCommand route, AdminWriteCommand command);

    MutationResult createPrice(PriceCommand price, AdminWriteCommand command);

    MutationResult createBudget(BudgetCommand budget, AdminWriteCommand command);

    record RouteCommand(
            long routeId,
            String providerCode,
            String modelName,
            String fallbackProviderCode,
            String fallbackModelName,
            int priority,
            String routeVersion,
            String priceVersion,
            String budgetPolicyVersion,
            BigDecimal maxCost,
            Integer maxLatencyMs,
            String status,
            long revision) {}

    record PriceCommand(
            String providerCode,
            String modelName,
            String priceVersion,
            BigDecimal inputPricePerMillion,
            BigDecimal outputPricePerMillion,
            String currency,
            Instant effectiveAt) {}

    record BudgetCommand(
            String policyKey,
            String scene,
            String scopeType,
            int maxTotalTokens,
            BigDecimal maxCostCny,
            int maxModelCalls,
            int maxStepRetries,
            String windowType,
            String policyVersion) {}

    record GovernanceView(
            java.util.List<ProviderView> providers,
            java.util.List<ModelView> models,
            java.util.List<RouteView> routes,
            java.util.List<PriceRow> prices,
            java.util.List<BudgetRow> budgets,
            java.util.List<UsageView> usage) {}

    record ProviderView(
            long providerId,
            String providerCode,
            String displayName,
            String status,
            String endpointConfigKey,
            boolean configured,
            String fingerprint,
            long revision) {}

    record ModelView(
            long modelId,
            String providerCode,
            String modelName,
            String modelType,
            String status,
            Integer contextTokens,
            Integer maxOutputTokens,
            int timeoutMs,
            long revision) {}

    record RouteView(
            long routeId,
            long tenantId,
            String scene,
            String modelType,
            String providerCode,
            String modelName,
            String fallbackProviderCode,
            String fallbackModelName,
            Integer priority,
            String routeVersion,
            String priceVersion,
            String budgetPolicyVersion,
            BigDecimal maxCost,
            Integer maxLatencyMs,
            String status,
            long revision) {}

    record UsageView(
            String providerCode,
            String modelName,
            String scene,
            String status,
            long calls,
            long totalTokens,
            BigDecimal totalCost,
            BigDecimal averageLatencyMs,
            Instant firstSeenAt,
            Instant lastSeenAt) {}

    record MutationResult(boolean changed, long resourceId, String version, long revision) {}
}
