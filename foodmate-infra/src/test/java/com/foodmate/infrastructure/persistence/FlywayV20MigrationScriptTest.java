package com.foodmate.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** 校验 M2-3 管理写操作的 revision migration、校验和回滚文件齐全。 */
class FlywayV20MigrationScriptTest {
    private static final Path ROOT = Path.of("..", "script", "sql", "FoodMate");

    @Test
    void migrationAddsRevisionToAdminManagedResources() throws IOException {
        String sql =
                Files.readString(
                        ROOT.resolve(
                                Path.of("migration", "V20__m2_3_admin_management_contract.sql")));
        assertTrue(sql.contains("users"));
        assertTrue(sql.contains("tool_registries"));
        assertTrue(sql.contains("knowledge_documents"));
        assertTrue(sql.contains("messages"));
        assertTrue(sql.contains("revision BIGINT NOT NULL DEFAULT 1"));
        assertTrue(sql.contains("idx_users_revision"));
    }

    @Test
    void validationAndRollbackAreProvided() throws IOException {
        String validation =
                Files.readString(
                        ROOT.resolve(
                                Path.of(
                                        "validation",
                                        "V20__m2_3_admin_management_contract_validation.sql")));
        String rollback =
                Files.readString(
                        ROOT.resolve(
                                Path.of("rollback", "R20__m2_3_admin_management_contract.sql")));
        assertTrue(validation.contains("invalid_revision_rows"));
        assertTrue(rollback.contains("DROP COLUMN IF EXISTS revision"));
    }
}
