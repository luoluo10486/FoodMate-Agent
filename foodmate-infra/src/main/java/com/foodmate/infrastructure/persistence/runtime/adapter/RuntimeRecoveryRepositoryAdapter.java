package com.foodmate.infrastructure.persistence.runtime.adapter;

import com.foodmate.application.runtime.port.out.RuntimeRecoveryRepository;
import com.foodmate.infrastructure.persistence.adapter.MapperRepositoryAdapter;
import com.foodmate.infrastructure.persistence.runtime.RuntimeRecoveryMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnBean(RuntimeRecoveryMapper.class)
public class RuntimeRecoveryRepositoryAdapter
        extends MapperRepositoryAdapter<RuntimeRecoveryRepository> {
    public RuntimeRecoveryRepositoryAdapter(RuntimeRecoveryMapper mapper) {
        super(mapper, RuntimeRecoveryRepository.class);
    }
}
