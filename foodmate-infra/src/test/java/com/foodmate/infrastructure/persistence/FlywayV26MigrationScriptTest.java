package com.foodmate.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** 校验结构化 Agent 反馈迁移仅追加安全业务事实。 */
class FlywayV26MigrationScriptTest {
    private static final Path ROOT = Path.of("..", "script", "sql", "FoodMate");

    @Test
    void migrationDefinesFeedbackBindingAndSafetyFieldsWithoutDestructiveSql() throws Exception {
        String sql = Files.readString(ROOT.resolve("migration/V26__m1_4_agent_feedback.sql"));

        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS agent_feedback"));
        assertTrue(sql.contains("UNIQUE (user_id, message_id)"));
        assertTrue(sql.contains("UNIQUE (user_id, idempotency_key)"));
        assertTrue(sql.contains("high_risk BOOLEAN NOT NULL DEFAULT FALSE"));
        assertTrue(!sql.matches("(?is).*\\b(TRUNCATE|DELETE\\s+FROM|DROP\\s+TABLE)\\b.*"));
    }

    @Test
    void rollbackIsAReadOnlyPrecheck() throws Exception {
        String sql =
                Files.readString(ROOT.resolve("rollback/R26__m1_4_agent_feedback_precheck.sql"));

        assertTrue(sql.contains("Manual rollback precondition"));
        assertTrue(sql.contains("feedback_rows"));
        assertTrue(!sql.matches("(?is).*\\b(DROP\\s+TABLE|TRUNCATE|DELETE\\s+FROM)\\b.*"));
    }
}
