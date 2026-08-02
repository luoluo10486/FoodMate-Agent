package com.foodmate.infrastructure.persistence.runtime.adapter;

import com.foodmate.application.runtime.port.out.RuntimeRepository;
import com.foodmate.infrastructure.persistence.adapter.MapperRepositoryAdapter;
import com.foodmate.infrastructure.persistence.runtime.RuntimeGatewayMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnBean(RuntimeGatewayMapper.class)
public class RuntimeRepositoryAdapter extends MapperRepositoryAdapter<RuntimeRepository> {
    public RuntimeRepositoryAdapter(RuntimeGatewayMapper mapper) {
        super(mapper, RuntimeRepository.class);
    }
}
