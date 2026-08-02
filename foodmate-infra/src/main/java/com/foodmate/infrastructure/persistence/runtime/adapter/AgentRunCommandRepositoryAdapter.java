package com.foodmate.infrastructure.persistence.runtime.adapter;

import com.foodmate.application.runtime.port.out.AgentRunCommandRepository;
import com.foodmate.infrastructure.persistence.adapter.MapperRepositoryAdapter;
import com.foodmate.infrastructure.persistence.runtime.AgentRunCommandMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnBean(AgentRunCommandMapper.class)
public class AgentRunCommandRepositoryAdapter
        extends MapperRepositoryAdapter<AgentRunCommandRepository> {
    public AgentRunCommandRepositoryAdapter(AgentRunCommandMapper mapper) {
        super(mapper, AgentRunCommandRepository.class);
    }
}
