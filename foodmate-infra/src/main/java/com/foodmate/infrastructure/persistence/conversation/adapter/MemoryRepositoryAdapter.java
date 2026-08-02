package com.foodmate.infrastructure.persistence.conversation.adapter;

import com.foodmate.application.conversation.port.out.MemoryRepository;
import com.foodmate.infrastructure.persistence.adapter.MapperRepositoryAdapter;
import com.foodmate.infrastructure.persistence.conversation.MemoryMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnBean(MemoryMapper.class)
public class MemoryRepositoryAdapter extends MapperRepositoryAdapter<MemoryRepository> {
    public MemoryRepositoryAdapter(MemoryMapper mapper) {
        super(mapper, MemoryRepository.class);
    }
}
