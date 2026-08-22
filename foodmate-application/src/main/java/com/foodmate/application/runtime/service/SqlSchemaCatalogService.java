package com.foodmate.application.runtime.service;

import java.util.List;

/** Publishes the small, approved schema surface that SQL Agent may inspect. */
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
