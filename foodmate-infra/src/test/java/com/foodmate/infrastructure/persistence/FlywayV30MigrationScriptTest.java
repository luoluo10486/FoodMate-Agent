package com.foodmate.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** 校验餐食计划工具注册表 Schema 修正只新增 v2，不修改或删除历史业务数据。 */
class FlywayV30MigrationScriptTest {
    private static final Path ROOT = Path.of("..", "script", "sql", "FoodMate");

    @Test
    void migrationPublishesPayloadIdempotencyContractAsV2() throws Exception {
        String migration =
                Files.readString(ROOT.resolve("migration/V30__m2_2_meal_plan_tool_schema_fix.sql"));

        assertTrue(migration.contains("tool_id = 720007"));
        assertTrue(migration.contains("(721009,"));
        assertTrue(migration.contains("'v2'"));
        assertTrue(migration.contains("required\":[\"plan\"]"));
        assertTrue(!migration.contains("required\":[\"plan\",\"idempotencyKey\"]"));
        assertTrue(!migration.matches("(?is).*\\b(TRUNCATE|DELETE\\s+FROM|DROP\\s+TABLE)\\b.*"));
    }

    @Test
    void validationAndRollbackAreReadOnlyCompanionFiles() throws Exception {
        String validation =
                Files.readString(
                        ROOT.resolve(
                                "validation/V30__m2_2_meal_plan_tool_schema_fix_validation.sql"));
        String rollback =
                Files.readString(
                        ROOT.resolve("rollback/R30__m2_2_meal_plan_tool_schema_fix_precheck.sql"));

        assertTrue(validation.contains("invalid_meal_plan_schema"));
        assertTrue(validation.contains("current_version"));
        assertTrue(rollback.contains("active_v2_registry_rows"));
        assertTrue(rollback.contains("回滚前置检查") || rollback.contains("rollback precheck"));
        assertTrue(!rollback.matches("(?is).*\\b(TRUNCATE|DELETE\\s+FROM|DROP\\s+TABLE)\\b.*"));
    }
}
