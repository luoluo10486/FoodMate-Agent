package com.foodmate.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

class FlywayV25MigrationScriptTest {
    private static final Path ROOT = Paths.get("..", "script", "sql", "FoodMate");

    @Test
    void migrationCreatesPolicyHoldRequestAndTaskContractsWithoutDeletes() throws Exception {
        String sql = Files.readString(ROOT.resolve("migration/V25__m3_retention_governance.sql"));

        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS data_retention_policies"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS data_legal_holds"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS data_purge_requests"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS data_purge_tasks"));
        assertTrue(sql.contains("hard_delete_enabled BOOLEAN NOT NULL DEFAULT FALSE"));
        assertTrue(!sql.matches("(?is).*\\b(TRUNCATE|DELETE\\s+FROM|DROP\\s+TABLE)\\b.*"));
    }

    @Test
    void rollbackRequiresManualPreconditionAndDoesNotHideItsScope() throws Exception {
        String sql = Files.readString(ROOT.resolve("rollback/R25__m3_retention_governance.sql"));

        assertTrue(sql.contains("Manual rollback precondition"));
        assertTrue(sql.contains("data_purge_tasks"));
        assertTrue(sql.contains("data_retention_policies"));
    }
}
