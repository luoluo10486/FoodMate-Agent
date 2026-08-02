package com.foodmate.infrastructure.persistence.runtime.adapter;

import com.foodmate.application.runtime.port.out.OutboxRepository;
import com.foodmate.infrastructure.persistence.adapter.MapperRepositoryAdapter;
import com.foodmate.infrastructure.persistence.runtime.DispatchOutboxMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnBean(DispatchOutboxMapper.class)
public class OutboxRepositoryAdapter extends MapperRepositoryAdapter<OutboxRepository> {
    public OutboxRepositoryAdapter(DispatchOutboxMapper mapper) {
        super(mapper, OutboxRepository.class);
    }
}
