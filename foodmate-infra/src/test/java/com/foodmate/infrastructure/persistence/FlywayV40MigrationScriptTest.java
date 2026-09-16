package com.foodmate.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** 校验工具步骤跳过迁移具备只读配套检查，且不会删除既有业务数据。 */
class FlywayV40MigrationScriptTest {
    private static final Path ROOT = Path.of("..", "script", "sql", "FoodMate");

    @Test
    void migrationCreatesSkipCommandAndPolicyFields() throws Exception {
        String migration =
                Files.readString(ROOT.resolve("migration/V40__m2_2_tool_step_skip.sql"));

        assertTrue(migration.contains("agent_run_tool_skips"));
        assertTrue(migration.contains("skippable"));
        assertTrue(migration.contains("skip_requested_at"));
        assertFalse(migration.matches("(?is).*\\b(TRUNCATE|DELETE\\s+FROM|DROP\\s+TABLE)\\b.*"));
    }

    @Test
    void companionFilesRemainReadOnly() throws Exception {
        String validation =
                Files.readString(
                        ROOT.resolve("validation/V40__m2_2_tool_step_skip_validation.sql"));
        String rollback =
                Files.readString(
                        ROOT.resolve("rollback/R40__m2_2_tool_step_skip_precheck.sql"));

        assertTrue(validation.contains("invalid_skip_status_rows"));
        assertTrue(rollback.contains("rollback_precheck"));
        assertFalse(rollback.matches("(?is).*\\b(TRUNCATE|DELETE\\s+FROM|DROP\\s+TABLE)\\b.*"));
    }
}
