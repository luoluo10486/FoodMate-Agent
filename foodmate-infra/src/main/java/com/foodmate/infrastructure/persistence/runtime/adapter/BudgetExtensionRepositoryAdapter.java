package com.foodmate.infrastructure.persistence.runtime.adapter;

import com.foodmate.application.runtime.port.out.BudgetExtensionRepository;
import com.foodmate.infrastructure.persistence.adapter.MapperRepositoryAdapter;
import com.foodmate.infrastructure.persistence.runtime.BudgetExtensionMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnBean(BudgetExtensionMapper.class)
public class BudgetExtensionRepositoryAdapter
        extends MapperRepositoryAdapter<BudgetExtensionRepository> {
    public BudgetExtensionRepositoryAdapter(BudgetExtensionMapper mapper) {
        super(mapper, BudgetExtensionRepository.class);
    }
}
