package com.foodmate.infrastructure.persistence.runtime;

import com.foodmate.application.runtime.port.out.ModelGovernanceRepository.ModelGovernanceSnapshot;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** PostgreSQL read adapter for the versioned model governance snapshot. */
@Mapper
public interface ModelGovernanceMapper {
    @Select(
            "SELECT r.scene,r.model_type AS modelType,COALESCE(r.route_version,'legacy-v1') AS routeVersion,r.provider_code AS providerCode,COALESCE(r.model_name,mc.model_name) AS modelName,r.fallback_provider_code AS fallbackProviderCode,r.fallback_model_name AS fallbackModelName,p.price_version AS priceVersion,p.input_price_per_million AS inputPricePerMillion,p.output_price_per_million AS outputPricePerMillion,b.policy_version AS budgetPolicyVersion,COALESCE(b.max_total_tokens,30000) AS maxTotalTokens,COALESCE(b.max_cost_cny,0.50) AS maxCostCny,COALESCE(b.max_model_calls,12) AS maxModelCalls,COALESCE(b.max_step_retries,2) AS maxStepRetries,COALESCE(mc.timeout_ms,15000) AS modelTimeoutMs,p.effective_at AS priceEffectiveAt FROM model_route_rules r LEFT JOIN model_catalog mc ON mc.provider_code=r.provider_code AND (r.model_name IS NULL OR mc.model_name=r.model_name) AND mc.model_type=r.model_type AND mc.status='active' AND mc.is_deleted=FALSE LEFT JOIN LATERAL (SELECT p0.price_version,p0.input_price_per_million,p0.output_price_per_million,p0.effective_at FROM model_price_versions p0 WHERE p0.provider_code=r.provider_code AND p0.model_name=COALESCE(r.model_name,mc.model_name) AND (r.price_version IS NULL OR p0.price_version=r.price_version) AND p0.status='active' AND p0.is_deleted=FALSE ORDER BY p0.effective_at DESC,p0.price_version DESC LIMIT 1) p ON TRUE LEFT JOIN LATERAL (SELECT b0.policy_version,b0.max_total_tokens,b0.max_cost_cny,b0.max_model_calls,b0.max_step_retries FROM model_budget_policies b0 WHERE b0.scene=r.scene AND (r.budget_policy_version IS NULL OR b0.policy_version=r.budget_policy_version) AND b0.scope_type IN ('global','scene') AND b0.status='active' AND b0.is_deleted=FALSE ORDER BY CASE WHEN b0.scope_type='scene' THEN 0 ELSE 1 END,b0.revision DESC,b0.budget_policy_id DESC LIMIT 1) b ON TRUE WHERE r.tenant_id=0 AND r.scene=#{scene} AND r.model_type=#{modelType} AND r.status='active' AND r.is_deleted=FALSE ORDER BY r.priority ASC,r.revision DESC,r.model_route_rule_id DESC LIMIT 1")
    List<ModelGovernanceSnapshot> resolve(
            @Param("scene") String scene, @Param("modelType") String modelType);
}
