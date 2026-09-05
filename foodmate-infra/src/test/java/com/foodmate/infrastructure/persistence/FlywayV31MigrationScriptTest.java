package com.foodmate.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** 校验记忆来源失效迁移只增加可追溯字段，不删除既有数据。 */
class FlywayV31MigrationScriptTest {
    private static final Path ROOT = Path.of("..", "script", "sql", "FoodMate");

    @Test
    void migrationAddsSourceAndSuppressionMetadata() throws Exception {
        String migration =
                Files.readString(
                        ROOT.resolve("migration/V31__m1_4_memory_invalidation_boundary.sql"));

        assertTrue(migration.contains("source_message_ids"));
        assertTrue(migration.contains("suppressed_source_message_ids"));
        assertTrue(migration.contains("jsonb_typeof"));
        assertFalse(migration.matches("(?is).*\\b(TRUNCATE|DELETE\\s+FROM|DROP\\s+TABLE)\\b.*"));
    }

    @Test
    void validationAndRollbackAreReadOnlyCompanionFiles() throws Exception {
        String validation =
                Files.readString(
                        ROOT.resolve(
                                "validation/V31__m1_4_memory_invalidation_boundary_validation.sql"));
        String rollback =
                Files.readString(
                        ROOT.resolve(
                                "rollback/R31__m1_4_memory_invalidation_boundary_precheck.sql"));

        assertTrue(validation.contains("invalid_source_rows"));
        assertTrue(rollback.contains("manual review"));
        assertFalse(rollback.matches("(?is).*\\b(TRUNCATE|DELETE\\s+FROM|DROP\\s+TABLE)\\b.*"));
    }
}
