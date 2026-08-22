package com.foodmate.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** 校验 M2-1 导入、投递和结果 Inbox 的静态契约。 */
class FlywayV16V17KnowledgeMigrationScriptTest {
    private static final Path ROOT = Path.of("..", "script", "sql", "FoodMate");

    @Test
    void importMigrationPinsStateIdempotencyAndTopicContracts() throws IOException {
        String migration =
                Files.readString(
                        ROOT.resolve(Path.of("migration", "V16__m2_1_knowledge_import.sql")));
        String validation =
                Files.readString(
                        ROOT.resolve(
                                Path.of("validation", "V16__m2_1_knowledge_import_validation.sql")));

        assertTrue(migration.contains("chk_knowledge_import_jobs_idempotency"));
        assertTrue(migration.contains("chk_knowledge_index_outbox_topic"));
        assertTrue(migration.contains("uk_knowledge_import_jobs_operator_idempotency"));
        assertTrue(validation.contains("invalid_import_jobs"));
        assertTrue(validation.contains("invalid_import_items"));
    }

    @Test
    void deliveryMigrationPinsResultAttemptsAndRollbackCleansReplacementIndex() throws IOException {
        String migration =
                Files.readString(
                        ROOT.resolve(Path.of("migration", "V17__m2_1_knowledge_delivery.sql")));
        String validation =
                Files.readString(
                        ROOT.resolve(
                                Path.of("validation", "V17__m2_1_knowledge_delivery_validation.sql")));
        String rollback =
                Files.readString(
                        ROOT.resolve(Path.of("rollback", "R17__m2_1_knowledge_delivery.sql")));

        assertTrue(
                migration.contains(
                        "attempt_count INT NOT NULL CHECK (attempt_count BETWEEN 1 AND 3)"));
        assertTrue(migration.contains("chk_knowledge_visibility_outbox_topic"));
        assertTrue(validation.contains("invalid_result_attempts"));
        assertTrue(
                validation.contains(
                        "uk_knowledge_documents_current_source_document_version"));
        assertTrue(
                rollback.contains(
                        "DROP INDEX IF EXISTS uk_knowledge_documents_current_source_document_version"));
    }
}
