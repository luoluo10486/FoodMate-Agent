package com.foodmate.infrastructure.persistence.runtime.adapter;

import com.foodmate.application.runtime.port.out.ToolSkipRepository;
import com.foodmate.infrastructure.persistence.adapter.MapperRepositoryAdapter;
import com.foodmate.infrastructure.persistence.runtime.ToolSkipMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

/** 单个工具步骤跳过请求的 PostgreSQL 适配器。 */
@Repository
@Profile("local")
public class ToolSkipRepositoryAdapter extends MapperRepositoryAdapter<ToolSkipRepository> {
    public ToolSkipRepositoryAdapter(ToolSkipMapper mapper) {
        super(mapper, ToolSkipRepository.class);
    }
}
