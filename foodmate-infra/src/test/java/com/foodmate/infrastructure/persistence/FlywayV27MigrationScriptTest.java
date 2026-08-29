package com.foodmate.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** 校验 M3 清理执行结果台账迁移只追加事实且不执行删除。 */
class FlywayV27MigrationScriptTest {
    private static final Path ROOT = Path.of("..", "script", "sql", "FoodMate");

    @Test
    void migrationCreatesIdempotentExecutionLedgerWithoutDestructiveSql() throws Exception {
        String sql = Files.readString(ROOT.resolve("migration/V27__m3_purge_execution_results.sql"));

        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS data_purge_task_results"));
        assertTrue(sql.contains("UNIQUE (task_id, result_digest)"));
        assertTrue(sql.contains("verified_absent BOOLEAN NOT NULL DEFAULT FALSE"));
        assertTrue(!sql.matches("(?is).*\\b(TRUNCATE|DELETE\\s+FROM|DROP\\s+TABLE)\\b.*"));
    }

    @Test
    void rollbackIsAnExplicitReadOnlyPrecheck() throws Exception {
        String sql =
                Files.readString(
                        ROOT.resolve("rollback/R27__m3_purge_execution_results_precheck.sql"));

        assertTrue(sql.contains("Manual rollback precondition"));
        assertTrue(sql.contains("successful_unverified_rows"));
        assertTrue(!sql.matches("(?is).*\\b(TRUNCATE|DELETE\\s+FROM|DROP\\s+TABLE)\\b.*"));
    }
}
