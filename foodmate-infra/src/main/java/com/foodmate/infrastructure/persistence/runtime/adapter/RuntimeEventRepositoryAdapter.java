package com.foodmate.infrastructure.persistence.runtime.adapter;

import com.foodmate.application.runtime.port.out.RuntimeEventRepository;
import com.foodmate.infrastructure.persistence.adapter.MapperRepositoryAdapter;
import com.foodmate.infrastructure.persistence.runtime.V1RuntimeEventMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("local")
public class RuntimeEventRepositoryAdapter extends MapperRepositoryAdapter<RuntimeEventRepository> {
    public RuntimeEventRepositoryAdapter(V1RuntimeEventMapper mapper) {
        super(mapper, RuntimeEventRepository.class);
    }
}
