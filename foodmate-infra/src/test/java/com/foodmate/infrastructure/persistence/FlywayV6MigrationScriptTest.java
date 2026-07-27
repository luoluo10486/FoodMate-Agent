package com.foodmate.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * 校验 M1-4 V6 迁移脚本的 MQ 传输、published 语义与 DLQ 结构。
 */
class FlywayV6MigrationScriptTest {
    private static final Path V6_SCRIPT = Path.of(
            "..", "script", "sql", "FoodMate", "migration", "V6__m1_4_mq_transport.sql");
    private static final Path R6_SCRIPT = Path.of(
            "..", "script", "sql", "FoodMate", "rollback", "R6__m1_4_mq_transport.sql");

    @Test
    void migrationAddsPublishedStatusAndBrokerConfirmation() throws IOException {
        String sql = Files.readString(V6_SCRIPT);
        assertTrue(sql.contains("'published'"), "outbox 必须支持 Broker 确认后的 published 状态");
        assertTrue(sql.contains("ADD COLUMN IF NOT EXISTS mq_message_id VARCHAR(128)"));
        assertTrue(sql.contains("ADD COLUMN IF NOT EXISTS published_at TIMESTAMPTZ"));
        assertTrue(sql.contains("chk_runtime_dispatch_outbox_published"),
                "published 必须携带 Broker 消息标识，否则无法与 DLQ 对账");
        assertTrue(sql.contains("chk_runtime_dispatch_outbox_transport"));
    }

    @Test
    void migrationDefinesDeadLetterTableWithIdempotencyAndReconciliation() throws IOException {
        String sql = Files.readString(V6_SCRIPT);
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS runtime_message_dlq"));
        assertTrue(sql.contains("uk_runtime_message_dlq_message UNIQUE (consumer_group, mq_message_id)"),
                "DLQ 消费者可能重复投递，必须按消费组 + 消息 ID 幂等");
        assertTrue(sql.contains("chk_runtime_message_dlq_state"));
        assertTrue(sql.contains("reconciliation_state"), "DLQ 需要对账状态而不是直接判定 Run 失败");
        // run_id 是文本列：DLQ 里可能出现已不存在的 Run，不能用外键。
        assertFalse(sql.contains("run_id BIGINT NOT NULL REFERENCES agent_runs"));
    }

    @Test
    void rollbackNormalizesPublishedRowsBeforeRestoringCheck() throws IOException {
        String sql = Files.readString(R6_SCRIPT);
        assertTrue(sql.indexOf("UPDATE runtime_dispatch_outbox SET status = 'delivered'")
                        < sql.indexOf("ADD CONSTRAINT chk_runtime_dispatch_outbox_status"),
                "必须先把 published 归一为 delivered，再恢复 V4 状态集合，否则 CHECK 会失败");
        assertTrue(sql.contains("DROP TABLE IF EXISTS runtime_message_dlq"));
    }
}
