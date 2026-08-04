package com.foodmate.infrastructure.persistence.runtime.adapter;

import com.foodmate.application.runtime.port.out.BudgetExtensionRepository;
import com.foodmate.infrastructure.persistence.adapter.MapperRepositoryAdapter;
import com.foodmate.infrastructure.persistence.runtime.BudgetExtensionMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("local")
public class BudgetExtensionRepositoryAdapter
        extends MapperRepositoryAdapter<BudgetExtensionRepository> {
    public BudgetExtensionRepositoryAdapter(BudgetExtensionMapper mapper) {
        super(mapper, BudgetExtensionRepository.class);
    }
}
