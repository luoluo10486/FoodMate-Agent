package com.foodmate.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** 校验 M1-5 统一写操作幂等审计字段的人工 SQL 结构。 */
class FlywayV14MigrationScriptTest {
    private static final Path MIGRATION =
            Path.of(
                    "..",
                    "script",
                    "sql",
                    "FoodMate",
                    "migration",
                    "V14__m1_5_operation_idempotency.sql");
    private static final Path VALIDATION =
            Path.of(
                    "..",
                    "script",
                    "sql",
                    "FoodMate",
                    "validation",
                    "V14__m1_5_operation_idempotency_validation.sql");
    private static final Path ROLLBACK =
            Path.of(
                    "..",
                    "script",
                    "sql",
                    "FoodMate",
                    "rollback",
                    "R14__m1_5_operation_idempotency.sql");

    @Test
    void migrationAddsDigestFieldsAndUniqueIndex() throws IOException {
        String sql = Files.readString(MIGRATION);

        assertTrue(sql.contains("idempotency_key VARCHAR(128)"));
        assertTrue(sql.contains("parameters_digest VARCHAR(128)"));
        assertTrue(sql.contains("uk_operation_audits_operator_idempotency"));
        assertTrue(sql.contains("operator_id, idempotency_key"));
    }

    @Test
    void validationAndRollbackAreProvided() throws IOException {
        String validation = Files.readString(VALIDATION);
        String rollback = Files.readString(ROLLBACK);

        assertTrue(validation.contains("parameters_digest"));
        assertTrue(validation.contains("idx_operation_audits_idempotency_lookup"));
        assertTrue(rollback.contains("DROP COLUMN IF EXISTS idempotency_key"));
        assertTrue(rollback.contains("DROP COLUMN IF EXISTS parameters_digest"));
    }
}
