package com.foodmate.application.runtime.service;

import java.util.List;

/** 发布 SQL Agent 可以检查的精简已批准 Schema 范围。 */
public interface SqlSchemaCatalogService {
    CatalogView current(long datasourceId);

    record CatalogView(long datasourceId, String version, List<TableView> tables) {}

    record TableView(String schemaName, String tableName, Scope scope, List<FieldView> fields) {}

    record FieldView(
            String name,
            String description,
            String dataType,
            boolean filterable,
            boolean aggregatable,
            boolean sortable,
            String sampleSql) {}

    enum Scope {
        USER,
        USER_VIA_FOOD_LOG,
        TENANT,
        PUBLIC
    }
}
