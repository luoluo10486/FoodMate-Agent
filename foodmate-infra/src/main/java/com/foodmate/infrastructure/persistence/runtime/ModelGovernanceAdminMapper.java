package com.foodmate.infrastructure.persistence.runtime;

import com.foodmate.application.runtime.port.out.ModelGovernanceAdminRepository.BudgetRow;
import com.foodmate.application.runtime.port.out.ModelGovernanceAdminRepository.ModelRow;
import com.foodmate.application.runtime.port.out.ModelGovernanceAdminRepository.PriceRow;
import com.foodmate.application.runtime.port.out.ModelGovernanceAdminRepository.ProviderRow;
import com.foodmate.application.runtime.port.out.ModelGovernanceAdminRepository.RouteRow;
import com.foodmate.application.runtime.port.out.ModelGovernanceAdminRepository.RouteUpdate;
import com.foodmate.application.runtime.port.out.ModelGovernanceAdminRepository.UsageAggregate;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 模型治理汇总及受控状态、版本写入的 SQL 映射。 */
@Mapper
public interface ModelGovernanceAdminMapper {
    @Select(
            "SELECT provider_id AS providerId,provider_code AS providerCode,display_name AS displayName,status,endpoint_config_key AS endpointConfigKey,revision FROM model_providers WHERE is_deleted=FALSE ORDER BY provider_code")
    List<ProviderRow> providers();

    @Select(
            "SELECT model_id AS modelId,provider_code AS providerCode,model_name AS modelName,model_type AS modelType,status,context_tokens AS contextTokens,max_output_tokens AS maxOutputTokens,timeout_ms AS timeoutMs,revision FROM model_catalog WHERE is_deleted=FALSE ORDER BY provider_code,model_name")
    List<ModelRow> models();

    @Select(
            "SELECT model_route_rule_id AS routeId,tenant_id AS tenantId,scene,model_type AS modelType,provider_code AS providerCode,model_name AS modelName,fallback_provider_code AS fallbackProviderCode,fallback_model_name AS fallbackModelName,priority,route_version AS routeVersion,price_version AS priceVersion,budget_policy_version AS budgetPolicyVersion,max_cost AS maxCost,max_latency_ms AS maxLatencyMs,status,revision FROM model_route_rules WHERE is_deleted=FALSE ORDER BY scene,model_type,priority,model_route_rule_id")
    List<RouteRow> routes();

    @Select(
            "SELECT price_version_id AS priceVersionId,provider_code AS providerCode,model_name AS modelName,price_version AS priceVersion,input_price_per_million AS inputPricePerMillion,output_price_per_million AS outputPricePerMillion,currency,status,effective_at AS effectiveAt FROM model_price_versions WHERE is_deleted=FALSE ORDER BY provider_code,model_name,effective_at DESC LIMIT 200")
    List<PriceRow> prices();

    @Select(
            "SELECT budget_policy_id AS budgetPolicyId,policy_key AS policyKey,scene,scope_type AS scopeType,max_total_tokens AS maxTotalTokens,max_cost_cny AS maxCostCny,max_model_calls AS maxModelCalls,max_step_retries AS maxStepRetries,window_type AS windowType,policy_version AS policyVersion,status,revision FROM model_budget_policies WHERE is_deleted=FALSE ORDER BY scene,policy_key,revision DESC LIMIT 200")
    List<BudgetRow> budgets();

    @Select(
            "<script>SELECT provider_code AS providerCode,model_name AS modelName,scene,status,COUNT(*) AS calls,COALESCE(SUM(COALESCE((usage_json-&gt;&gt;'total_tokens')::bigint,0)),0) AS totalTokens,COALESCE(SUM(cost_amount),0) AS totalCost,COALESCE(AVG(latency_ms),0) AS averageLatencyMs,MIN(created_at) AS firstSeenAt,MAX(created_at) AS lastSeenAt FROM model_usage_logs WHERE is_deleted=FALSE <if test='query.from != null'>AND created_at &gt;= #{query.from}</if><if test='query.to != null'>AND created_at &lt; #{query.to}</if> GROUP BY provider_code,model_name,scene,status ORDER BY lastSeenAt DESC</script>")
    List<UsageAggregate> usage(@Param("query") UsageQuery query);

    @Select(
            "SELECT provider_id AS providerId,provider_code AS providerCode,display_name AS displayName,status,endpoint_config_key AS endpointConfigKey,revision FROM model_providers WHERE provider_code=#{providerCode} AND is_deleted=FALSE")
    ProviderRow provider(@Param("providerCode") String providerCode);

    @Select(
            "SELECT model_id AS modelId,provider_code AS providerCode,model_name AS modelName,model_type AS modelType,status,context_tokens AS contextTokens,max_output_tokens AS maxOutputTokens,timeout_ms AS timeoutMs,revision FROM model_catalog WHERE model_id=#{modelId} AND is_deleted=FALSE")
    ModelRow model(@Param("modelId") long modelId);

    @Select(
            "SELECT model_route_rule_id AS routeId,tenant_id AS tenantId,scene,model_type AS modelType,provider_code AS providerCode,model_name AS modelName,fallback_provider_code AS fallbackProviderCode,fallback_model_name AS fallbackModelName,priority,route_version AS routeVersion,price_version AS priceVersion,budget_policy_version AS budgetPolicyVersion,max_cost AS maxCost,max_latency_ms AS maxLatencyMs,status,revision FROM model_route_rules WHERE model_route_rule_id=#{routeId} AND is_deleted=FALSE")
    RouteRow route(@Param("routeId") long routeId);

    @Select(
            "SELECT price_version_id AS priceVersionId,provider_code AS providerCode,model_name AS modelName,price_version AS priceVersion,input_price_per_million AS inputPricePerMillion,output_price_per_million AS outputPricePerMillion,currency,status,effective_at AS effectiveAt FROM model_price_versions WHERE provider_code=#{providerCode} AND model_name=#{modelName} AND price_version=#{priceVersion} AND is_deleted=FALSE")
    PriceRow price(
            @Param("providerCode") String providerCode,
            @Param("modelName") String modelName,
            @Param("priceVersion") String priceVersion);

    @Select(
            "SELECT budget_policy_id AS budgetPolicyId,policy_key AS policyKey,scene,scope_type AS scopeType,max_total_tokens AS maxTotalTokens,max_cost_cny AS maxCostCny,max_model_calls AS maxModelCalls,max_step_retries AS maxStepRetries,window_type AS windowType,policy_version AS policyVersion,status,revision FROM model_budget_policies WHERE policy_key=#{policyKey} AND policy_version=#{policyVersion} AND is_deleted=FALSE")
    BudgetRow budget(
            @Param("policyKey") String policyKey, @Param("policyVersion") String policyVersion);

    @Update(
            "UPDATE model_providers SET status=#{status},updated_at=CURRENT_TIMESTAMP,updated_by=#{operatorId},revision=revision+1 WHERE provider_code=#{providerCode} AND is_deleted=FALSE AND revision=#{revision}")
    int updateProviderStatus(
            @Param("providerCode") String providerCode,
            @Param("status") String status,
            @Param("operatorId") long operatorId,
            @Param("revision") long revision);

    @Update(
            "UPDATE model_catalog SET status=#{status},updated_at=CURRENT_TIMESTAMP,updated_by=#{operatorId},revision=revision+1 WHERE model_id=#{modelId} AND is_deleted=FALSE AND revision=#{revision}")
    int updateModelStatus(
            @Param("modelId") long modelId,
            @Param("status") String status,
            @Param("operatorId") long operatorId,
            @Param("revision") long revision);

    @Update(
            "UPDATE model_route_rules SET provider_code=#{update.providerCode},model_name=#{update.modelName},fallback_provider_code=#{update.fallbackProviderCode},fallback_model_name=#{update.fallbackModelName},priority=#{update.priority},route_version=#{update.routeVersion},price_version=#{update.priceVersion},budget_policy_version=#{update.budgetPolicyVersion},max_cost=#{update.maxCost},max_latency_ms=#{update.maxLatencyMs},status=#{update.status},updated_at=CURRENT_TIMESTAMP,updated_by=#{operatorId},revision=revision+1 WHERE model_route_rule_id=#{update.routeId} AND is_deleted=FALSE AND revision=#{revision}")
    int updateRoute(
            @Param("update") RouteUpdate update,
            @Param("operatorId") long operatorId,
            @Param("revision") long revision);

    @Insert(
            "INSERT INTO model_price_versions(price_version_id,provider_code,model_name,price_version,input_price_per_million,output_price_per_million,currency,status,effective_at,created_by,updated_by) VALUES (#{price.priceVersionId},#{price.providerCode},#{price.modelName},#{price.priceVersion},#{price.inputPricePerMillion},#{price.outputPricePerMillion},#{price.currency},'active',#{price.effectiveAt},#{operatorId},#{operatorId})")
    int insertPrice(@Param("price") PriceInsert price, @Param("operatorId") long operatorId);

    @Insert(
            "INSERT INTO model_budget_policies(budget_policy_id,policy_key,scene,scope_type,max_total_tokens,max_cost_cny,max_model_calls,max_step_retries,window_type,policy_version,status,created_by,updated_by) VALUES (#{budget.budgetPolicyId},#{budget.policyKey},#{budget.scene},#{budget.scopeType},#{budget.maxTotalTokens},#{budget.maxCostCny},#{budget.maxModelCalls},#{budget.maxStepRetries},#{budget.windowType},#{budget.policyVersion},'active',#{operatorId},#{operatorId})")
    int insertBudget(@Param("budget") BudgetInsert budget, @Param("operatorId") long operatorId);

    record UsageQuery(Instant from, Instant to) {}

    record PriceInsert(
            long priceVersionId,
            String providerCode,
            String modelName,
            String priceVersion,
            java.math.BigDecimal inputPricePerMillion,
            java.math.BigDecimal outputPricePerMillion,
            String currency,
            Instant effectiveAt) {}

    record BudgetInsert(
            long budgetPolicyId,
            String policyKey,
            String scene,
            String scopeType,
            int maxTotalTokens,
            java.math.BigDecimal maxCostCny,
            int maxModelCalls,
            int maxStepRetries,
            String windowType,
            String policyVersion) {}
}
