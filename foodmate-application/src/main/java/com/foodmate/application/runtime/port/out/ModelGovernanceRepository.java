package com.foodmate.application.runtime.port.out;

import java.math.BigDecimal;
import java.time.Instant;

/** Model governance read port used to freeze a route, price and budget snapshot per run. */
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
