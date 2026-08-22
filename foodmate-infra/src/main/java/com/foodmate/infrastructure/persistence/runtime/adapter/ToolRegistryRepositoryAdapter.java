package com.foodmate.infrastructure.persistence.runtime.adapter;

import com.foodmate.application.runtime.port.out.ToolRegistryRepository;
import com.foodmate.application.runtime.port.out.ToolRegistryRepository.ToolDefinition;
import com.foodmate.infrastructure.persistence.runtime.ToolRegistryMapper;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

/** PostgreSQL 工具注册表适配器。 */
@Repository
@Profile("local")
public class ToolRegistryRepositoryAdapter implements ToolRegistryRepository {
    private final ToolRegistryMapper mapper;

    public ToolRegistryRepositoryAdapter(ToolRegistryMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<ToolDefinition> findAll() {
        return mapper.findAll();
    }

    @Override
    public ToolDefinition findCurrent(String name) {
        return mapper.findCurrent(name);
    }

    @Override
    public ToolDefinition findVersion(String name, String version) {
        return mapper.findVersion(name, version);
    }
}
