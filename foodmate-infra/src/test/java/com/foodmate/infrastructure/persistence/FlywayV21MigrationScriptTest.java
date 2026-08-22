package com.foodmate.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Verifies the M1 model governance migration package is complete and secret-free by design. */
class FlywayV21MigrationScriptTest {
    private static final Path ROOT = Path.of("..", "script", "sql", "FoodMate");

    @Test
    void migrationDefinesVersionedGovernanceObjects() throws IOException {
        String sql =
                Files.readString(
                        ROOT.resolve(
                                Path.of("migration", "V21__m1_model_governance_contract.sql")));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS model_providers"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS model_catalog"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS model_price_versions"));
        assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS model_budget_policies"));
        assertTrue(sql.contains("route_version"));
        assertTrue(sql.contains("budget_policy_version"));
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
                                        "V21__m1_model_governance_contract_validation.sql")));
        String rollback =
                Files.readString(
                        ROOT.resolve(
                                Path.of("rollback", "R21__m1_model_governance_contract.sql")));
        assertTrue(validation.contains("invalid_rows"));
        assertTrue(rollback.contains("DROP TABLE IF EXISTS model_budget_policies"));
        assertTrue(rollback.contains("DROP COLUMN IF EXISTS route_version"));
    }
}
