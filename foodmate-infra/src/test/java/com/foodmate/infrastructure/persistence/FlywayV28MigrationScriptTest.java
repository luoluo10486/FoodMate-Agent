package com.foodmate.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** 校验 M2-1 手动重试能够追加独立索引 Outbox 事实。 */
class FlywayV28MigrationScriptTest {
    private static final Path ROOT = Path.of("..", "script", "sql", "FoodMate");

    @Test
    void migrationRemovesOnlyThePerItemOutboxUniquenessAndAddsLookupIndex() throws Exception {
        String sql =
                Files.readString(ROOT.resolve("migration/V28__m2_1_knowledge_retry_outbox.sql"));

        assertTrue(sql.contains("DROP CONSTRAINT IF EXISTS uk_knowledge_index_outbox_item_topic"));
        assertTrue(sql.contains("idx_knowledge_index_outbox_item_topic"));
        assertTrue(!sql.matches("(?is).*\\b(TRUNCATE|DELETE\\s+FROM|DROP\\s+TABLE)\\b.*"));
    }

    @Test
    void validationAndRollbackAreExplicitAboutDuplicateFacts() throws Exception {
        String validation =
                Files.readString(
                        ROOT.resolve("validation/V28__m2_1_knowledge_retry_outbox_validation.sql"));
        String rollback =
                Files.readString(
                        ROOT.resolve("rollback/R28__m2_1_knowledge_retry_outbox_precheck.sql"));

        assertTrue(validation.contains("duplicate_item_topic_facts"));
        assertTrue(rollback.contains("duplicate_item_topic_facts"));
        assertTrue(rollback.contains("Manual rollback precondition"));
        assertTrue(!rollback.matches("(?is).*\\b(TRUNCATE|DELETE\\s+FROM|DROP\\s+TABLE)\\b.*"));
    }
}
