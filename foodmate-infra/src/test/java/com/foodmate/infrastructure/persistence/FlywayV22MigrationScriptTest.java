package com.foodmate.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Verifies provider optimistic-concurrency migration assets are present and reversible. */
class FlywayV22MigrationScriptTest {
    private static final Path ROOT = Path.of("..", "script", "sql", "FoodMate");

    @Test
    void migrationAddsProviderRevisionWithoutSecrets() throws IOException {
        String sql =
                Files.readString(
                        ROOT.resolve(Path.of("migration", "V22__m1_model_provider_revision.sql")));

        assertTrue(sql.contains("ADD COLUMN IF NOT EXISTS revision BIGINT NOT NULL DEFAULT 1"));
        assertTrue(sql.contains("chk_model_providers_revision"));
        assertTrue(sql.contains("idx_model_providers_revision"));
        assertTrue(!sql.toLowerCase().contains("api_key"));
        assertTrue(!sql.toLowerCase().contains("secret_value"));
    }

    @Test
    void validationAndRollbackAreProvided() throws IOException {
        String validation =
                Files.readString(
                        ROOT.resolve(
                                Path.of(
                                        "validation",
                                        "V22__m1_model_provider_revision_validation.sql")));
        String rollback =
                Files.readString(
                        ROOT.resolve(Path.of("rollback", "R22__m1_model_provider_revision.sql")));

        assertTrue(validation.contains("invalid_revision_rows"));
        assertTrue(rollback.contains("DROP INDEX IF EXISTS idx_model_providers_revision"));
        assertTrue(rollback.contains("DROP COLUMN IF EXISTS revision"));
    }
}
