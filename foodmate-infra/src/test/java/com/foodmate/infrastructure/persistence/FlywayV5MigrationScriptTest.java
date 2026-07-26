package com.foodmate.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * 校验 M1-4 V5 迁移脚本的 continuation、superseded 与预算快照结构。
 */
class FlywayV5MigrationScriptTest {
    private static final Path V5_SCRIPT = Path.of(
            "..", "script", "sql", "FoodMate", "migration", "V5__m1_4_continuation_and_budget.sql");
    private static final Path R5_SCRIPT = Path.of(
            "..", "script", "sql", "FoodMate", "rollback", "R5__m1_4_continuation_and_budget.sql");

    @Test
    void migrationAddsContinuationColumnsAndSupersededState() throws IOException {
        String sql = Files.readString(V5_SCRIPT);
        assertTrue(sql.contains("ADD COLUMN IF NOT EXISTS parent_run_id BIGINT"));
        assertTrue(sql.contains("ADD COLUMN IF NOT EXISTS superseded_by_run_id BIGINT"));
        assertTrue(sql.contains("ADD COLUMN IF NOT EXISTS continuation_reason VARCHAR(64)"));
        assertTrue(sql.contains("ADD COLUMN IF NOT EXISTS result_type VARCHAR(32)"));
        assertTrue(sql.contains("'superseded'"), "status check must include the superseded terminal state");
        assertTrue(sql.contains("chk_agent_runs_continuation_pair"), "continuation must pair parent id with reason");
        assertTrue(sql.contains("uk_agent_runs_parent_active"), "one parent run allows at most one continuation");
    }

    @Test
    void migrationDefinesBudgetSnapshotAndExtensionTables() throws IOException {
        String sql = Files.readString(V5_SCRIPT);
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS agent_run_budget_snapshots"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS agent_run_budget_extensions"));
        assertTrue(sql.contains("uk_agent_run_budget_revision UNIQUE (agent_run_id, revision)"));
        assertTrue(sql.contains("chk_agent_run_budget_confirmation"), "extension snapshots must carry a confirmation digest");
        assertTrue(sql.contains("queue_timeout_seconds"), "timeout snapshot columns must be frozen with the budget");
        assertTrue(sql.contains("waiting_user_timeout_seconds"));
    }

    @Test
    void rollbackRestoresV1StatusCheckAndDropsBudgetTables() throws IOException {
        String sql = Files.readString(R5_SCRIPT);
        assertTrue(sql.contains("DROP TABLE IF EXISTS agent_run_budget_extensions"));
        assertTrue(sql.contains("DROP TABLE IF EXISTS agent_run_budget_snapshots"));
        assertTrue(sql.contains("DROP COLUMN IF EXISTS parent_run_id"));
        assertTrue(!sql.contains("'superseded'"), "rollback must restore the V1 status set");
    }
}
