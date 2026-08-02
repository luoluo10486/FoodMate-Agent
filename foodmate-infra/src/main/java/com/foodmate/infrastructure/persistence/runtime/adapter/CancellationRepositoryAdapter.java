package com.foodmate.infrastructure.persistence.runtime.adapter;

import com.foodmate.application.runtime.port.out.CancellationRepository;
import com.foodmate.infrastructure.persistence.adapter.MapperRepositoryAdapter;
import com.foodmate.infrastructure.persistence.runtime.CancellationMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnBean(CancellationMapper.class)
public class CancellationRepositoryAdapter extends MapperRepositoryAdapter<CancellationRepository> {
    public CancellationRepositoryAdapter(CancellationMapper mapper) {
        super(mapper, CancellationRepository.class);
    }
}
