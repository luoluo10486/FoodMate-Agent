package com.foodmate.application.runtime.port.out;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** 模型治理安全读取和 superadmin 变更的持久化端口。 */
public interface ModelGovernanceAdminRepository {
    GovernanceState state(UsageQuery query);

    ProviderRow provider(String providerCode);

    ModelRow model(long modelId);

    RouteRow route(long routeId);

    PriceRow price(String providerCode, String modelName, String priceVersion);

    BudgetRow budget(String policyKey, String policyVersion);

    int updateProviderStatus(String providerCode, String status, long operatorId, long revision);

    int updateModelStatus(long modelId, String status, long operatorId, long revision);

    int updateRoute(RouteUpdate update, long operatorId, long revision);

    int insertPrice(PriceInsert price, long operatorId);

    int insertBudget(BudgetInsert budget, long operatorId);

    record UsageQuery(Instant from, Instant to) {}

    record GovernanceState(
            List<ProviderRow> providers,
            List<ModelRow> models,
            List<RouteRow> routes,
            List<PriceRow> prices,
            List<BudgetRow> budgets,
            List<UsageAggregate> usage) {
        public GovernanceState {
            providers = providers == null ? List.of() : List.copyOf(providers);
            models = models == null ? List.of() : List.copyOf(models);
            routes = routes == null ? List.of() : List.copyOf(routes);
            prices = prices == null ? List.of() : List.copyOf(prices);
            budgets = budgets == null ? List.of() : List.copyOf(budgets);
            usage = usage == null ? List.of() : List.copyOf(usage);
        }
    }

    record ProviderRow(
            long providerId,
            String providerCode,
            String displayName,
            String status,
            String endpointConfigKey,
            long revision) {}

    record ModelRow(
            long modelId,
            String providerCode,
            String modelName,
            String modelType,
            String status,
            Integer contextTokens,
            Integer maxOutputTokens,
            int timeoutMs,
            long revision) {}

    record RouteRow(
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

    record PriceRow(
            long priceVersionId,
            String providerCode,
            String modelName,
            String priceVersion,
            BigDecimal inputPricePerMillion,
            BigDecimal outputPricePerMillion,
            String currency,
            String status,
            Instant effectiveAt) {}

    record BudgetRow(
            long budgetPolicyId,
            String policyKey,
            String scene,
            String scopeType,
            int maxTotalTokens,
            BigDecimal maxCostCny,
            int maxModelCalls,
            int maxStepRetries,
            String windowType,
            String policyVersion,
            String status,
            long revision) {}

    record UsageAggregate(
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

    record RouteUpdate(
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
            String status) {}

    record PriceInsert(
            long priceVersionId,
            String providerCode,
            String modelName,
            String priceVersion,
            BigDecimal inputPricePerMillion,
            BigDecimal outputPricePerMillion,
            String currency,
            Instant effectiveAt,
            long operatorId) {}

    record BudgetInsert(
            long budgetPolicyId,
            String policyKey,
            String scene,
            String scopeType,
            int maxTotalTokens,
            BigDecimal maxCostCny,
            int maxModelCalls,
            int maxStepRetries,
            String windowType,
            String policyVersion,
            long operatorId) {}
}
