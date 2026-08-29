package com.foodmate.application.runtime.service;

import com.foodmate.application.runtime.port.out.ModelGovernanceRepository;
import com.foodmate.application.runtime.port.out.ModelGovernanceRepository.ModelGovernanceSnapshot;
import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** 解析模型治理元数据，始终不接触供应商密钥。 */
@Service
public class ModelGovernanceService {
    private final ModelGovernanceRepository store;
    private final String providerCode;
    private final String modelName;
    private final String routeVersion;
    private final String priceVersion;
    private final String budgetPolicyVersion;
    private final int maxTotalTokens;
    private final BigDecimal maxCostCny;
    private final int maxModelCalls;
    private final int maxStepRetries;
    private final int modelTimeoutMs;

    @Autowired
    public ModelGovernanceService(
            ObjectProvider<ModelGovernanceRepository> storeProvider,
            @Value("${foodmate.model.governance.default-provider:deterministic}")
                    String providerCode,
            @Value("${foodmate.model.governance.default-model:qwen-plus}") String modelName,
            @Value("${foodmate.model.governance.route-version:env-v1}") String routeVersion,
            @Value("${foodmate.model.governance.price-version:env-v1}") String priceVersion,
            @Value("${foodmate.model.governance.budget-policy-version:env-v1}")
                    String budgetPolicyVersion,
            @Value("${FOODMATE_AGENT_MAX_TOKENS_PER_RUN:30000}") int maxTotalTokens,
            @Value("${FOODMATE_AGENT_MAX_COST_CNY_PER_RUN:0.50}") BigDecimal maxCostCny,
            @Value("${FOODMATE_AGENT_MAX_MODEL_CALLS:12}") int maxModelCalls,
            @Value("${FOODMATE_AGENT_MAX_STEP_RETRIES:2}") int maxStepRetries,
            @Value("${foodmate.model.timeout-ms:15000}") int modelTimeoutMs) {
        this.store = storeProvider == null ? null : storeProvider.getIfAvailable();
        this.providerCode = required(providerCode, "default provider");
        this.modelName = required(modelName, "default model");
        this.routeVersion = required(routeVersion, "route version");
        this.priceVersion = required(priceVersion, "price version");
        this.budgetPolicyVersion = required(budgetPolicyVersion, "budget policy version");
        if (maxTotalTokens <= 0
                || maxModelCalls <= 0
                || maxStepRetries < 0
                || modelTimeoutMs <= 0) {
            throw new IllegalStateException("model governance numeric settings are invalid");
        }
        if (maxCostCny == null || maxCostCny.signum() < 0) {
            throw new IllegalStateException("model governance cost budget is invalid");
        }
        this.maxTotalTokens = maxTotalTokens;
        this.maxCostCny = maxCostCny;
        this.maxModelCalls = maxModelCalls;
        this.maxStepRetries = maxStepRetries;
        this.modelTimeoutMs = modelTimeoutMs;
    }

    public ModelGovernanceSnapshot resolve(String scene, String modelType) {
        String safeScene = required(scene, "scene");
        String safeModelType = required(modelType, "model type");
        if (store != null) {
            ModelGovernanceSnapshot governed = store.resolve(safeScene, safeModelType);
            if (governed != null) {
                validate(governed);
                return governed;
            }
        }
        return new ModelGovernanceSnapshot(
                safeScene,
                safeModelType,
                routeVersion,
                providerCode,
                modelName,
                null,
                null,
                priceVersion,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                budgetPolicyVersion,
                maxTotalTokens,
                maxCostCny,
                maxModelCalls,
                maxStepRetries,
                modelTimeoutMs,
                Instant.EPOCH);
    }

    private static String required(String value, String label) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) throw new IllegalStateException(label + " must not be blank");
        return normalized;
    }

    private static void validate(ModelGovernanceSnapshot snapshot) {
        required(snapshot.providerCode(), "governed provider");
        required(snapshot.modelName(), "governed model");
        required(snapshot.routeVersion(), "governed route version");
        required(snapshot.budgetPolicyVersion(), "governed budget policy version");
        if (!"deterministic".equals(snapshot.providerCode())
                && (snapshot.inputPricePerMillion() == null
                        || snapshot.outputPricePerMillion() == null
                        || snapshot.priceVersion() == null
                        || snapshot.priceVersion().isBlank())) {
            throw new IllegalStateException("governed cloud route has no active price version");
        }
    }
}
