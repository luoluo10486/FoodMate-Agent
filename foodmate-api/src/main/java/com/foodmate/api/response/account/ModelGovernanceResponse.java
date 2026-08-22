package com.foodmate.api.response.account;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.foodmate.application.runtime.service.ModelGovernanceAdminService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Safe model governance response; provider credentials and raw prompts never enter this DTO. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ModelGovernanceResponse(
        List<Provider> providers,
        List<Model> models,
        List<Route> routes,
        List<Price> prices,
        List<Budget> budgets,
        List<Usage> usage) {
    public static ModelGovernanceResponse from(ModelGovernanceAdminService.GovernanceView value) {
        return new ModelGovernanceResponse(
                value.providers().stream().map(Provider::from).toList(),
                value.models().stream().map(Model::from).toList(),
                value.routes().stream().map(Route::from).toList(),
                value.prices().stream().map(Price::from).toList(),
                value.budgets().stream().map(Budget::from).toList(),
                value.usage().stream().map(Usage::from).toList());
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Provider(
            long providerId,
            String providerCode,
            String displayName,
            String status,
            String endpointConfigKey,
            boolean configured,
            String fingerprint,
            long revision) {
        static Provider from(ModelGovernanceAdminService.ProviderView value) {
            return new Provider(
                    value.providerId(),
                    value.providerCode(),
                    value.displayName(),
                    value.status(),
                    value.endpointConfigKey(),
                    value.configured(),
                    value.fingerprint(),
                    value.revision());
        }
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Model(
            long modelId,
            String providerCode,
            String modelName,
            String modelType,
            String status,
            Integer contextTokens,
            Integer maxOutputTokens,
            int timeoutMs,
            long revision) {
        static Model from(ModelGovernanceAdminService.ModelView value) {
            return new Model(
                    value.modelId(),
                    value.providerCode(),
                    value.modelName(),
                    value.modelType(),
                    value.status(),
                    value.contextTokens(),
                    value.maxOutputTokens(),
                    value.timeoutMs(),
                    value.revision());
        }
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Route(
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
            long revision) {
        static Route from(ModelGovernanceAdminService.RouteView value) {
            return new Route(
                    value.routeId(),
                    value.tenantId(),
                    value.scene(),
                    value.modelType(),
                    value.providerCode(),
                    value.modelName(),
                    value.fallbackProviderCode(),
                    value.fallbackModelName(),
                    value.priority(),
                    value.routeVersion(),
                    value.priceVersion(),
                    value.budgetPolicyVersion(),
                    value.maxCost(),
                    value.maxLatencyMs(),
                    value.status(),
                    value.revision());
        }
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Price(
            long priceVersionId,
            String providerCode,
            String modelName,
            String priceVersion,
            BigDecimal inputPricePerMillion,
            BigDecimal outputPricePerMillion,
            String currency,
            String status,
            Instant effectiveAt) {
        static Price from(
                com.foodmate.application.runtime.port.out.ModelGovernanceAdminRepository.PriceRow
                        value) {
            return new Price(
                    value.priceVersionId(),
                    value.providerCode(),
                    value.modelName(),
                    value.priceVersion(),
                    value.inputPricePerMillion(),
                    value.outputPricePerMillion(),
                    value.currency(),
                    value.status(),
                    value.effectiveAt());
        }
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Budget(
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
            long revision) {
        static Budget from(
                com.foodmate.application.runtime.port.out.ModelGovernanceAdminRepository.BudgetRow
                        value) {
            return new Budget(
                    value.budgetPolicyId(),
                    value.policyKey(),
                    value.scene(),
                    value.scopeType(),
                    value.maxTotalTokens(),
                    value.maxCostCny(),
                    value.maxModelCalls(),
                    value.maxStepRetries(),
                    value.windowType(),
                    value.policyVersion(),
                    value.status(),
                    value.revision());
        }
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Usage(
            String providerCode,
            String modelName,
            String scene,
            String status,
            long calls,
            long totalTokens,
            BigDecimal totalCost,
            BigDecimal averageLatencyMs,
            Instant firstSeenAt,
            Instant lastSeenAt) {
        static Usage from(ModelGovernanceAdminService.UsageView value) {
            return new Usage(
                    value.providerCode(),
                    value.modelName(),
                    value.scene(),
                    value.status(),
                    value.calls(),
                    value.totalTokens(),
                    value.totalCost(),
                    value.averageLatencyMs(),
                    value.firstSeenAt(),
                    value.lastSeenAt());
        }
    }
}
