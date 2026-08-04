package com.foodmate.infrastructure.persistence.runtime.adapter;

import com.foodmate.application.runtime.port.out.RuntimeRecoveryRepository;
import com.foodmate.infrastructure.persistence.adapter.MapperRepositoryAdapter;
import com.foodmate.infrastructure.persistence.runtime.RuntimeRecoveryMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("local")
public class RuntimeRecoveryRepositoryAdapter
        extends MapperRepositoryAdapter<RuntimeRecoveryRepository> {
    public RuntimeRecoveryRepositoryAdapter(RuntimeRecoveryMapper mapper) {
        super(mapper, RuntimeRecoveryRepository.class);
    }
}
