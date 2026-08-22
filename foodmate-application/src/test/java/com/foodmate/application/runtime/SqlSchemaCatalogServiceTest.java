package com.foodmate.application.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.foodmate.application.runtime.port.out.SqlSchemaCatalogRepository;
import com.foodmate.application.runtime.port.out.SqlSchemaCatalogRepository.CatalogField;
import com.foodmate.application.runtime.service.SqlSchemaCatalogService;
import com.foodmate.application.runtime.service.impl.SqlSchemaCatalogServiceImpl;
import com.foodmate.shared.error.BusinessException;
import java.util.List;
import org.junit.jupiter.api.Test;

class SqlSchemaCatalogServiceTest {
    @Test
    void onlyPublishesApprovedNonSensitiveFieldsWithStablePolicy() {
        var service =
                new SqlSchemaCatalogServiceImpl(
                        repository(
                                row("food_logs", "user_id", false),
                                row("food_logs", "meal_time", false),
                                row("users", "password_hash", false),
                                row("food_logs", "notes", true)));

        SqlSchemaCatalogService.CatalogView catalog = service.current(1L);

        assertEquals("catalog-v1", catalog.version());
        assertEquals(1, catalog.tables().size());
        assertEquals(SqlSchemaCatalogService.Scope.USER, catalog.tables().getFirst().scope());
        assertEquals(
                List.of("meal_time", "user_id"),
                catalog.tables().getFirst().fields().stream()
                        .map(SqlSchemaCatalogService.FieldView::name)
                        .toList());
    }

    @Test
    void failsClosedWhenCatalogHasNoApprovedFields() {
        var service = new SqlSchemaCatalogServiceImpl(repository(row("users", "user_id", false)));

        BusinessException exception =
                assertThrows(BusinessException.class, () -> service.current(1L));

        assertEquals("SQL_CATALOG_UNAVAILABLE", exception.errorCode().code());
    }

    @Test
    void failsClosedWhenRepositoryMixesDatasourceOrCatalogVersion() {
        var service =
                new SqlSchemaCatalogServiceImpl(
                        repository(
                                row("food_logs", "meal_time", false),
                                new CatalogField(
                                        2L,
                                        "catalog-v1",
                                        "public",
                                        "food_logs",
                                        "user_id",
                                        null,
                                        "bigint",
                                        false,
                                        null),
                                new CatalogField(
                                        1L,
                                        "catalog-v2",
                                        "public",
                                        "food_logs",
                                        "meal_type",
                                        null,
                                        "varchar",
                                        false,
                                        null)));

        BusinessException exception =
                assertThrows(BusinessException.class, () -> service.current(1L));

        assertEquals("SQL_CATALOG_UNAVAILABLE", exception.errorCode().code());
    }

    private static SqlSchemaCatalogRepository repository(CatalogField... rows) {
        return datasourceId -> List.of(rows);
    }

    private static CatalogField row(String table, String field, boolean sensitive) {
        return new CatalogField(
                1L, "catalog-v1", "public", table, field, null, "text", sensitive, null);
    }
}
