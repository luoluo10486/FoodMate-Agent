package com.foodmate.application.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.foodmate.application.runtime.service.SqlQueryGuard;
import com.foodmate.application.runtime.service.SqlSchemaCatalogService.CatalogView;
import com.foodmate.application.runtime.service.SqlSchemaCatalogService.FieldView;
import com.foodmate.application.runtime.service.SqlSchemaCatalogService.Scope;
import com.foodmate.application.runtime.service.SqlSchemaCatalogService.TableView;
import com.foodmate.application.runtime.service.impl.JSqlParserQueryGuard;
import com.foodmate.shared.error.BusinessException;
import java.util.List;
import org.junit.jupiter.api.Test;

class JSqlParserQueryGuardTest {
    private final SqlQueryGuard guard = new JSqlParserQueryGuard();

    @Test
    void parsesAllowlistedReadAndInjectsTrustedUserScopeAndLimit() {
        SqlQueryGuard.GuardedQuery query =
                guard.guard(
                        "SELECT meal_time FROM food_logs WHERE meal_type = 'lunch'",
                        catalog(),
                        42L);

        assertEquals(List.of(42L), query.parameters());
        org.junit.jupiter.api.Assertions.assertTrue(query.statement().contains("user_id = ?"));
        org.junit.jupiter.api.Assertions.assertTrue(
                query.statement().contains("is_deleted = false"));
        org.junit.jupiter.api.Assertions.assertTrue(
                query.statement().toLowerCase().contains("limit 500"));
    }

    @Test
    void rejectsWriteMultiStatementAndCommentPayloads() {
        for (String sql :
                List.of(
                        "UPDATE food_logs SET notes = 'x'",
                        "SELECT meal_time FROM food_logs; DELETE FROM food_logs",
                        "SELECT meal_time FROM food_logs /* bypass */")) {
            BusinessException exception =
                    assertThrows(BusinessException.class, () -> guard.guard(sql, catalog(), 42L));
            assertEquals("SQL_GUARD_DENIED", exception.errorCode().code());
        }
    }

    @Test
    void rejectsUnknownTableColumnsWildcardsAndFunctions() {
        assertEquals(
                "SQL_SCHEMA_DENIED",
                assertThrows(
                                BusinessException.class,
                                () ->
                                        guard.guard(
                                                "SELECT password_hash FROM users", catalog(), 42L))
                        .errorCode()
                        .code());
        assertEquals(
                "SQL_SCHEMA_DENIED",
                assertThrows(
                                BusinessException.class,
                                () -> guard.guard("SELECT * FROM food_logs", catalog(), 42L))
                        .errorCode()
                        .code());
        assertEquals(
                "SQL_SCHEMA_DENIED",
                assertThrows(
                                BusinessException.class,
                                () ->
                                        guard.guard(
                                                "SELECT pg_sleep(1) FROM food_logs",
                                                catalog(),
                                                42L))
                        .errorCode()
                        .code());
        assertEquals(
                "SQL_SCHEMA_DENIED",
                assertThrows(
                                BusinessException.class,
                                () ->
                                        guard.guard(
                                                "SELECT meal_time FROM other.food_logs",
                                                catalog(),
                                                42L))
                        .errorCode()
                        .code());
    }

    @Test
    void preservesCteReadButStillScopesItsBaseTable() {
        SqlQueryGuard.GuardedQuery query =
                guard.guard(
                        "WITH recent AS (SELECT meal_time FROM food_logs) SELECT meal_time FROM recent",
                        catalog(),
                        42L);

        assertEquals(List.of(42L), query.parameters());
        org.junit.jupiter.api.Assertions.assertTrue(query.statement().contains("user_id = ?"));
    }

    @Test
    void wrapsUserPredicateBeforeAddingScopeFilters() {
        SqlQueryGuard.GuardedQuery query =
                guard.guard(
                        "SELECT meal_time FROM food_logs WHERE meal_type = 'lunch' OR meal_type = 'dinner'",
                        catalog(),
                        42L);

        assertEquals(
                "WHERE ((meal_type = 'lunch' OR meal_type = 'dinner') AND food_logs.is_deleted = false) AND food_logs.user_id = ? LIMIT 500",
                query.statement().substring(query.statement().indexOf("WHERE")));
    }

    @Test
    void rejectsUnscopedDerivedTablesAndExpressionSubqueries() {
        for (String sql :
                List.of(
                        "SELECT x.meal_time FROM (SELECT meal_time FROM food_logs) x",
                        "SELECT (SELECT meal_time FROM food_logs) FROM food_logs")) {
            assertEquals(
                    "SQL_GUARD_DENIED",
                    assertThrows(BusinessException.class, () -> guard.guard(sql, catalog(), 42L))
                            .errorCode()
                            .code());
        }
    }

    private static CatalogView catalog() {
        return new CatalogView(
                1L,
                "catalog-v1",
                List.of(
                        new TableView(
                                "public",
                                "food_logs",
                                Scope.USER,
                                List.of(
                                        field("food_log_id"),
                                        field("user_id"),
                                        field("meal_time"),
                                        field("meal_type"),
                                        field("is_deleted"))),
                        new TableView(
                                "public",
                                "nutrition_foods",
                                Scope.PUBLIC,
                                List.of(field("standard_name"), field("is_deleted")))));
    }

    private static FieldView field(String name) {
        return new FieldView(name, null, "text", true, false, true, null);
    }
}
