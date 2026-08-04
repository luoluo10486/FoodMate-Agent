package com.foodmate.infrastructure.persistence.runtime.adapter;

import com.foodmate.application.runtime.port.out.RuntimeRepository;
import com.foodmate.infrastructure.persistence.adapter.MapperRepositoryAdapter;
import com.foodmate.infrastructure.persistence.runtime.RuntimeGatewayMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("local")
public class RuntimeRepositoryAdapter extends MapperRepositoryAdapter<RuntimeRepository> {
    public RuntimeRepositoryAdapter(RuntimeGatewayMapper mapper) {
        super(mapper, RuntimeRepository.class);
    }
}
