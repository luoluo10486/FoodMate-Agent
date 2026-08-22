package com.foodmate.infrastructure.persistence.runtime.adapter;

import com.foodmate.application.runtime.port.out.ModelGovernanceAdminRepository;
import com.foodmate.application.runtime.port.out.ModelGovernanceAdminRepository.BudgetInsert;
import com.foodmate.application.runtime.port.out.ModelGovernanceAdminRepository.BudgetRow;
import com.foodmate.application.runtime.port.out.ModelGovernanceAdminRepository.GovernanceState;
import com.foodmate.application.runtime.port.out.ModelGovernanceAdminRepository.ModelRow;
import com.foodmate.application.runtime.port.out.ModelGovernanceAdminRepository.PriceInsert;
import com.foodmate.application.runtime.port.out.ModelGovernanceAdminRepository.PriceRow;
import com.foodmate.application.runtime.port.out.ModelGovernanceAdminRepository.ProviderRow;
import com.foodmate.application.runtime.port.out.ModelGovernanceAdminRepository.RouteRow;
import com.foodmate.application.runtime.port.out.ModelGovernanceAdminRepository.RouteUpdate;
import com.foodmate.infrastructure.persistence.runtime.ModelGovernanceAdminMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

/** PostgreSQL adapter for model governance administration. */
@Repository
@Profile("local")
public class ModelGovernanceAdminRepositoryAdapter implements ModelGovernanceAdminRepository {
    private final ModelGovernanceAdminMapper mapper;

    public ModelGovernanceAdminRepositoryAdapter(ModelGovernanceAdminMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public GovernanceState state(UsageQuery query) {
        return new GovernanceState(
                mapper.providers(),
                mapper.models(),
                mapper.routes(),
                mapper.prices(),
                mapper.budgets(),
                mapper.usage(
                        new ModelGovernanceAdminMapper.UsageQuery(
                                query == null ? null : query.from(),
                                query == null ? null : query.to())));
    }

    @Override
    public ProviderRow provider(String providerCode) {
        return mapper.provider(providerCode);
    }

    @Override
    public ModelRow model(long modelId) {
        return mapper.model(modelId);
    }

    @Override
    public RouteRow route(long routeId) {
        return mapper.route(routeId);
    }

    @Override
    public PriceRow price(String providerCode, String modelName, String priceVersion) {
        return mapper.price(providerCode, modelName, priceVersion);
    }

    @Override
    public BudgetRow budget(String policyKey, String policyVersion) {
        return mapper.budget(policyKey, policyVersion);
    }

    @Override
    public int updateProviderStatus(
            String providerCode, String status, long operatorId, long revision) {
        return mapper.updateProviderStatus(providerCode, status, operatorId, revision);
    }

    @Override
    public int updateModelStatus(long modelId, String status, long operatorId, long revision) {
        return mapper.updateModelStatus(modelId, status, operatorId, revision);
    }

    @Override
    public int updateRoute(RouteUpdate update, long operatorId, long revision) {
        return mapper.updateRoute(update, operatorId, revision);
    }

    @Override
    public int insertPrice(PriceInsert price, long operatorId) {
        return mapper.insertPrice(
                new ModelGovernanceAdminMapper.PriceInsert(
                        price.priceVersionId(),
                        price.providerCode(),
                        price.modelName(),
                        price.priceVersion(),
                        price.inputPricePerMillion(),
                        price.outputPricePerMillion(),
                        price.currency(),
                        price.effectiveAt()),
                operatorId);
    }

    @Override
    public int insertBudget(BudgetInsert budget, long operatorId) {
        return mapper.insertBudget(
                new ModelGovernanceAdminMapper.BudgetInsert(
                        budget.budgetPolicyId(),
                        budget.policyKey(),
                        budget.scene(),
                        budget.scopeType(),
                        budget.maxTotalTokens(),
                        budget.maxCostCny(),
                        budget.maxModelCalls(),
                        budget.maxStepRetries(),
                        budget.windowType(),
                        budget.policyVersion()),
                operatorId);
    }
}
