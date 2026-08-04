package com.foodmate.infrastructure.persistence.conversation.adapter;

import com.foodmate.application.conversation.port.out.MemoryRepository;
import com.foodmate.infrastructure.persistence.adapter.MapperRepositoryAdapter;
import com.foodmate.infrastructure.persistence.conversation.MemoryMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("local")
public class MemoryRepositoryAdapter extends MapperRepositoryAdapter<MemoryRepository> {
    public MemoryRepositoryAdapter(MemoryMapper mapper) {
        super(mapper, MemoryRepository.class);
    }
}
