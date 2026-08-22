package com.foodmate.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** 校验 M2-2 七工具注册表 seed、校验和回滚文件存在且覆盖同一版本。 */
class FlywayV18MigrationScriptTest {
    private static final Path ROOT = Path.of("..", "script", "sql", "FoodMate");

    @Test
    void seedContainsAllAuthoritativeToolsAndVersionedSchemas() throws IOException {
        String sql =
                Files.readString(
                        ROOT.resolve(Path.of("migration", "V18__m2_2_tool_registry_seed.sql")));

        for (String name :
                new String[] {
                    "calculator",
                    "time_parser",
                    "knowledge_search",
                    "database_query",
                    "food_log_writer",
                    "plan_validator",
                    "meal_plan.save_plan"
                }) {
            assertTrue(sql.contains("'" + name + "'"));
        }
        assertTrue(sql.contains("tool_schema_versions"));
        assertTrue(sql.contains("\"approval\":\"required\""));
        assertTrue(sql.contains("ON CONFLICT DO NOTHING"));
    }

    @Test
    void validationAndRollbackAreProvided() throws IOException {
        String validation =
                Files.readString(
                        ROOT.resolve(
                                Path.of(
                                        "validation",
                                        "V18__m2_2_tool_registry_seed_validation.sql")));
        String rollback =
                Files.readString(
                        ROOT.resolve(Path.of("rollback", "R18__m2_2_tool_registry_seed.sql")));

        assertTrue(validation.contains("active_seed_tools"));
        assertTrue(validation.contains("current_version='v1'"));
        assertTrue(rollback.contains("721001"));
        assertTrue(rollback.contains("720001"));
    }
}
