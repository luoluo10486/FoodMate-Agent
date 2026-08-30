package com.foodmate.infrastructure.persistence.runtime.adapter;

import com.foodmate.application.runtime.port.out.ModelGovernanceRepository;
import com.foodmate.application.runtime.port.out.ModelGovernanceRepository.ModelGovernanceSnapshot;
import com.foodmate.infrastructure.persistence.runtime.ModelGovernanceMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

/** 模型治理端口的 PostgreSQL 实现。 */
@Repository
@Profile("local")
public class ModelGovernanceRepositoryAdapter implements ModelGovernanceRepository {
    private final ModelGovernanceMapper mapper;

    public ModelGovernanceRepositoryAdapter(ModelGovernanceMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public ModelGovernanceSnapshot resolve(String scene, String modelType) {
        return mapper.resolve(scene, modelType).stream().findFirst().orElse(null);
    }
}
