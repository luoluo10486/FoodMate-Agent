package com.foodmate.application.runtime.port.out;

import java.math.BigDecimal;
import java.time.Instant;

/** 模型治理读取端口，用于为每个 AgentRun 固化路由、价格和预算快照。 */
public interface ModelGovernanceRepository {
    /**
     * Returns the active route and its versioned policy bindings, or {@code null} if none exists.
     */
    ModelGovernanceSnapshot resolve(String scene, String modelType);

    record ModelGovernanceSnapshot(
            String scene,
            String modelType,
            String routeVersion,
            String providerCode,
            String modelName,
            String fallbackProviderCode,
            String fallbackModelName,
            String priceVersion,
            BigDecimal inputPricePerMillion,
            BigDecimal outputPricePerMillion,
            String budgetPolicyVersion,
            int maxTotalTokens,
            BigDecimal maxCostCny,
            int maxModelCalls,
            int maxStepRetries,
            int modelTimeoutMs,
            Instant priceEffectiveAt) {}
}
