package com.foodmate.infrastructure.persistence.runtime.adapter;

import com.foodmate.application.runtime.port.out.SqlSchemaCatalogRepository;
import com.foodmate.application.runtime.port.out.SqlSchemaCatalogRepository.CatalogField;
import com.foodmate.infrastructure.persistence.runtime.SqlSchemaCatalogMapper;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

/** 已批准 SQL Schema Catalog 的 PostgreSQL 适配器。 */
@Repository
@Profile("local")
public class SqlSchemaCatalogRepositoryAdapter implements SqlSchemaCatalogRepository {
    private final SqlSchemaCatalogMapper mapper;

    public SqlSchemaCatalogRepositoryAdapter(SqlSchemaCatalogMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<CatalogField> findActiveFields(long datasourceId) {
        return mapper.findActiveFields(datasourceId);
    }
}
