package com.foodmate.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** 校验 M3 DLQ 重放的 Outbox、幂等和原始消息快照结构。 */
class FlywayV24MigrationScriptTest {
    private static final Path V24_SCRIPT =
            Path.of("..", "script", "sql", "FoodMate", "migration", "V24__m3_dlq_replay.sql");
    private static final Path R24_SCRIPT =
            Path.of("..", "script", "sql", "FoodMate", "rollback", "R24__m3_dlq_replay.sql");

    @Test
    void migrationDefinesSafeReplayFactsAndActiveDlqUniqueness() throws IOException {
        String sql = Files.readString(V24_SCRIPT);

        assertTrue(sql.contains("ADD COLUMN IF NOT EXISTS raw_payload_text TEXT"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS runtime_dlq_replay_outbox"));
        assertTrue(
                sql.contains(
                        "uk_runtime_dlq_replay_idempotency UNIQUE (operator_id, idempotency_key)"));
        assertTrue(sql.contains("uk_runtime_dlq_replay_active_dlq"));
        assertTrue(sql.contains("original_message_id VARCHAR(128) NOT NULL"));
        assertTrue(sql.contains("request_hash VARCHAR(71) NOT NULL"));
        assertTrue(sql.contains("broker_message_id VARCHAR(128)"));
    }

    @Test
    void rollbackRemovesOnlyReplayStructure() throws IOException {
        String sql = Files.readString(R24_SCRIPT);

        assertTrue(sql.contains("DROP TABLE IF EXISTS runtime_dlq_replay_outbox"));
        assertTrue(sql.contains("DROP COLUMN IF EXISTS raw_payload_text"));
    }
}
