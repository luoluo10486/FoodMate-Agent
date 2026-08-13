package com.foodmate.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** 校验 M1-5 饮食记录、营养目录和写确认的人工 SQL 结构。 */
class FlywayV13MigrationScriptTest {
    private static final Path MIGRATION =
            Path.of(
                    "..",
                    "script",
                    "sql",
                    "FoodMate",
                    "migration",
                    "V13__m1_5_food_log_nutrition_approval.sql");
    private static final Path VALIDATION =
            Path.of(
                    "..",
                    "script",
                    "sql",
                    "FoodMate",
                    "validation",
                    "V13__m1_5_food_log_nutrition_approval_validation.sql");
    private static final Path ROLLBACK =
            Path.of(
                    "..",
                    "script",
                    "sql",
                    "FoodMate",
                    "rollback",
                    "R13__m1_5_food_log_nutrition_approval.sql");

    @Test
    void migrationRequiresEmptyFoodLogsAndRemovesLegacyJson() throws IOException {
        String sql = Files.readString(MIGRATION);

        assertTrue(sql.contains("IF EXISTS (SELECT 1 FROM food_logs)"));
        assertTrue(sql.contains("DROP COLUMN IF EXISTS items_json"));
        assertTrue(sql.contains("DROP COLUMN IF EXISTS nutrition_json"));
        assertTrue(sql.contains("idempotency_key VARCHAR(128)"));
        assertTrue(sql.contains("revision BIGINT NOT NULL DEFAULT 1"));
    }

    @Test
    void migrationDefinesM15TablesAndPrecisionConstraints() throws IOException {
        String sql = Files.readString(MIGRATION);

        assertTrue(sql.contains("CREATE TABLE food_log_items"));
        assertTrue(sql.contains("CREATE TABLE nutrition_foods"));
        assertTrue(sql.contains("CREATE TABLE nutrition_unit_conversions"));
        assertTrue(sql.contains("CREATE TABLE approval_requests"));
        assertTrue(sql.contains("NUMERIC(12, 4)"));
        assertTrue(sql.contains("chk_food_log_items_matched_snapshot"));
        assertTrue(sql.contains("chk_food_log_items_unmatched_snapshot"));
        assertTrue(sql.contains("uk_approval_requests_user_idempotency"));
    }

    @Test
    void validationAndRollbackAreProvided() throws IOException {
        String validation = Files.readString(VALIDATION);
        String rollback = Files.readString(ROLLBACK);

        assertTrue(validation.contains("food_logs_with_legacy_json"));
        assertTrue(validation.contains("invalid_matched_food_items"));
        assertTrue(rollback.contains("R13 requires empty V13 tables"));
        assertTrue(rollback.contains("ADD COLUMN IF NOT EXISTS items_json"));
        assertTrue(rollback.contains("ADD COLUMN IF NOT EXISTS nutrition_json"));
    }
}
