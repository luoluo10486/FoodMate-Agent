package com.foodmate.application.runtime.port.out;

import java.util.List;

/** Provides the approved, non-secret SQL catalog from the persistence boundary. */
public interface SqlSchemaCatalogRepository {
    List<CatalogField> findActiveFields(long datasourceId);

    record CatalogField(
            long datasourceId,
            String catalogVersion,
            String schemaName,
            String tableName,
            String fieldName,
            String fieldDescription,
            String dataType,
            boolean sensitive,
            String sampleSql) {}
}
