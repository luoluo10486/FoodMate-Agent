package com.foodmate.infrastructure.config;

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
import com.foodmate.application.runtime.port.out.ModelGovernanceAdminRepository.UsageQuery;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** local-stub 使用的空治理存储，不伪装为已持久化状态。 */
@Configuration
@Profile("local-stub")
public class ModelGovernanceStubConfig {
    @Bean
    ModelGovernanceAdminRepository modelGovernanceAdminRepository() {
        return new ModelGovernanceAdminRepository() {
            @Override
            public GovernanceState state(UsageQuery query) {
                return new GovernanceState(
                        List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
            }

            @Override
            public ProviderRow provider(String providerCode) {
                return null;
            }

            @Override
            public ModelRow model(long modelId) {
                return null;
            }

            @Override
            public RouteRow route(long routeId) {
                return null;
            }

            @Override
            public PriceRow price(String providerCode, String modelName, String priceVersion) {
                return null;
            }

            @Override
            public BudgetRow budget(String policyKey, String policyVersion) {
                return null;
            }

            @Override
            public int updateProviderStatus(
                    String providerCode, String status, long operatorId, long revision) {
                return 0;
            }

            @Override
            public int updateModelStatus(
                    long modelId, String status, long operatorId, long revision) {
                return 0;
            }

            @Override
            public int updateRoute(RouteUpdate update, long operatorId, long revision) {
                return 0;
            }

            @Override
            public int insertPrice(PriceInsert price, long operatorId) {
                return 0;
            }

            @Override
            public int insertBudget(BudgetInsert budget, long operatorId) {
                return 0;
            }
        };
    }
}
