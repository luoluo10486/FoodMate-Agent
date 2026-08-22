package com.foodmate.application.runtime.service.impl;

import com.foodmate.application.runtime.port.out.SqlSchemaCatalogRepository;
import com.foodmate.application.runtime.port.out.SqlSchemaCatalogRepository.CatalogField;
import com.foodmate.application.runtime.service.SqlSchemaCatalogService;
import com.foodmate.application.runtime.service.SqlSchemaCatalogService.CatalogView;
import com.foodmate.application.runtime.service.SqlSchemaCatalogService.FieldView;
import com.foodmate.application.runtime.service.SqlSchemaCatalogService.Scope;
import com.foodmate.application.runtime.service.SqlSchemaCatalogService.TableView;
import com.foodmate.shared.error.BusinessException;
import com.foodmate.shared.error.ErrorCode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Applies the application-owned table and field allowlist to database catalog rows. */
@Service
public class SqlSchemaCatalogServiceImpl implements SqlSchemaCatalogService {
    private static final Map<String, Scope> APPROVED_TABLES =
            Map.of(
                    "food_logs", Scope.USER,
                    "food_log_items", Scope.USER_VIA_FOOD_LOG,
                    "meal_plans", Scope.USER,
                    "shopping_lists", Scope.USER,
                    "nutrition_foods", Scope.PUBLIC,
                    "knowledge_documents", Scope.TENANT);

    private final SqlSchemaCatalogRepository repository;

    @Autowired
    public SqlSchemaCatalogServiceImpl(SqlSchemaCatalogRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    @Override
    public CatalogView current(long datasourceId) {
        if (datasourceId <= 0) throw new BusinessException(ErrorCode.SQL_CATALOG_UNAVAILABLE);
        List<CatalogField> rows = repository.findActiveFields(datasourceId);
        if (rows == null || rows.isEmpty())
            throw new BusinessException(ErrorCode.SQL_CATALOG_UNAVAILABLE);

        String version =
                rows.stream()
                        .filter(row -> row != null)
                        .map(CatalogField::catalogVersion)
                        .filter(value -> value != null && !value.isBlank())
                        .findFirst()
                        .orElseThrow(
                                () -> new BusinessException(ErrorCode.SQL_CATALOG_UNAVAILABLE));
        if (rows.stream()
                .filter(row -> row != null)
                .anyMatch(
                        row ->
                                row.datasourceId() != datasourceId
                                        || !version.equals(row.catalogVersion())))
            throw new BusinessException(ErrorCode.SQL_CATALOG_UNAVAILABLE);
        Map<String, List<CatalogField>> grouped = new LinkedHashMap<>();
        for (CatalogField row : rows) {
            if (row == null || row.sensitive() || !isApproved(row, datasourceId)) continue;
            grouped.computeIfAbsent(tableKey(row), ignored -> new ArrayList<>()).add(row);
        }
        List<TableView> tables =
                grouped.entrySet().stream()
                        .map(entry -> table(entry.getValue()))
                        .filter(table -> !table.fields().isEmpty())
                        .sorted(Comparator.comparing(TableView::tableName))
                        .toList();
        if (tables.isEmpty()) throw new BusinessException(ErrorCode.SQL_CATALOG_UNAVAILABLE);
        return new CatalogView(datasourceId, version, tables);
    }

    private static boolean isApproved(CatalogField row, long datasourceId) {
        return row.datasourceId() == datasourceId
                && row.schemaName() != null
                && !row.schemaName().isBlank()
                && row.tableName() != null
                && APPROVED_TABLES.containsKey(row.tableName().toLowerCase());
    }

    private static String tableKey(CatalogField row) {
        return row.schemaName().toLowerCase() + "." + row.tableName().toLowerCase();
    }

    private static TableView table(List<CatalogField> rows) {
        CatalogField first = rows.getFirst();
        List<FieldView> fields =
                rows.stream()
                        .map(SqlSchemaCatalogServiceImpl::field)
                        .sorted(Comparator.comparing(FieldView::name))
                        .toList();
        return new TableView(
                first.schemaName().toLowerCase(),
                first.tableName().toLowerCase(),
                APPROVED_TABLES.get(first.tableName().toLowerCase()),
                fields);
    }

    private static FieldView field(CatalogField row) {
        String fieldName = row.fieldName().toLowerCase();
        boolean aggregate =
                switch (fieldName) {
                    case "meal_time",
                            "calories_kcal_per_100",
                            "protein_g_per_100",
                            "fat_g_per_100",
                            "carbs_g_per_100",
                            "calories_kcal",
                            "protein_g",
                            "fat_g",
                            "carbs_g",
                            "amount",
                            "quantity" ->
                            true;
                    default -> false;
                };
        boolean filterable = !fieldName.endsWith("_json") && !"notes".equals(fieldName);
        boolean sortable = filterable && !"user_id".equals(fieldName);
        return new FieldView(
                fieldName,
                row.fieldDescription(),
                row.dataType(),
                filterable,
                aggregate,
                sortable,
                row.sampleSql());
    }
}
