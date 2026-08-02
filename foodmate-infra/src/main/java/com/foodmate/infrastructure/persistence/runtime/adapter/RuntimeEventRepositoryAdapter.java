package com.foodmate.infrastructure.persistence.runtime.adapter;

import com.foodmate.application.runtime.port.out.RuntimeEventRepository;
import com.foodmate.infrastructure.persistence.adapter.MapperRepositoryAdapter;
import com.foodmate.infrastructure.persistence.runtime.V1RuntimeEventMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnBean(V1RuntimeEventMapper.class)
public class RuntimeEventRepositoryAdapter extends MapperRepositoryAdapter<RuntimeEventRepository> {
    public RuntimeEventRepositoryAdapter(V1RuntimeEventMapper mapper) {
        super(mapper, RuntimeEventRepository.class);
    }
}
