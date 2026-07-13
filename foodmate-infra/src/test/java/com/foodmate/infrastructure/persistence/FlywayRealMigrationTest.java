package com.foodmate.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 在真实 PostgreSQL 实例上执行 Flyway 迁移验证。
 *
 * P0-3: 确保迁移脚本可在 PostgreSQL 中执行，
 * 所有核心表已创建，约束和索引存在，重复执行幂等。
 */
@Testcontainers
class FlywayRealMigrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("foodmate_test")
            .withUsername("test")
            .withPassword("test");

    private static String jdbcUrl;
    private static String username;
    private static String password;

    @BeforeAll
    static void setup() {
        jdbcUrl = postgres.getJdbcUrl();
        username = postgres.getUsername();
        password = postgres.getPassword();
    }

    @Test
    void flywayMigrateCreatesAllCoreTables() {
        // Act: execute Flyway migration
        Flyway flyway = Flyway.configure()
                .dataSource(jdbcUrl, username, password)
                .locations("classpath:db/migration")
                .load();
        flyway.migrate();

        // Assert: all 24 core tables exist
        List<String> actualTables = queryTables();
        for (String table : CORE_TABLES) {
            assertTrue(actualTables.contains(table),
                    "Core table '" + table + "' must be created by Flyway migration");
        }
        assertEquals(CORE_TABLES.size(), actualTables.size(),
                "Exactly " + CORE_TABLES.size() + " core tables expected");
    }

    @Test
    void flywayMigrateIsIdempotent() {
        // Arrange: first migration
        Flyway flyway = Flyway.configure()
                .dataSource(jdbcUrl, username, password)
                .locations("classpath:db/migration")
                .load();
        flyway.migrate();

        // Act: second migrate — should be a no-op
        assertDoesNotThrow(() -> {
            var result = flyway.migrate();
            assertTrue(result.migrationsExecuted == 0,
                    "Second migrate should execute 0 migrations (idempotent)");
        });
    }

    @Test
    void flywayValidateSucceeds() {
        // Arrange
        Flyway flyway = Flyway.configure()
                .dataSource(jdbcUrl, username, password)
                .locations("classpath:db/migration")
                .load();
        flyway.migrate();

        // Act & Assert: validate checksum should match
        assertDoesNotThrow(flyway::validate,
                "Flyway validate must pass after a clean migration");
    }

    @Test
    void schemaHasRequiredIndexes() throws SQLException {
        // Arrange
        Flyway flyway = Flyway.configure()
                .dataSource(jdbcUrl, username, password)
                .locations("classpath:db/migration")
                .load();
        flyway.migrate();

        // Act
        List<String> indexes = queryIndexes();

        // Assert: baseline indexes defined in the migration
        assertTrue(indexes.contains("idx_sessions_user_last_message_at"));
        assertTrue(indexes.contains("idx_messages_session_sequence"));
        assertTrue(indexes.contains("idx_agent_runs_session_created_at"));
        assertTrue(indexes.contains("idx_tool_calls_run_created_at"));
        assertTrue(indexes.contains("idx_food_logs_user_meal_time"));
        assertTrue(indexes.contains("idx_knowledge_documents_tenant_status"));
    }

    @Test
    void schemaHasRequiredUniqueConstraints() throws SQLException {
        // Arrange
        Flyway flyway = Flyway.configure()
                .dataSource(jdbcUrl, username, password)
                .locations("classpath:db/migration")
                .load();
        flyway.migrate();

        // Act
        List<String> uniqueConstraints = queryUniqueConstraints();

        // Assert: key unique constraints exist
        // Note: index names may differ; we check by table+column combinations
        assertTrue(hasUniqueOnTable("users", "user_no", uniqueConstraints));
        assertTrue(hasUniqueOnTable("auth_refresh_tokens", "token_hash", uniqueConstraints));
    }

    // ── Core tables list (matching FlywayMigrationScriptTest) ────────────

    private static final List<String> CORE_TABLES = List.of(
            "users", "user_profiles", "auth_refresh_tokens", "user_avatar_assets",
            "sessions", "messages", "agent_runs", "tool_calls",
            "food_logs", "analysis_reports", "meal_plans", "shopping_lists",
            "user_memories", "session_summaries",
            "knowledge_documents", "knowledge_chunks",
            "data_sources", "schema_catalogs", "sql_query_audits",
            "tool_registries", "tool_schema_versions",
            "model_usage_logs", "model_route_rules",
            "operation_audits"
    );

    // ── Query helpers ───────────────────────────────────────────────────

    private List<String> queryTables() {
        List<String> tables = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password);
             ResultSet rs = conn.getMetaData().getTables(null, "public", null, new String[]{"TABLE"})) {
            while (rs.next()) {
                tables.add(rs.getString("TABLE_NAME"));
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return tables;
    }

    private List<String> queryIndexes() throws SQLException {
        List<String> indexes = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password);
             ResultSet rs = conn.getMetaData().getIndexInfo(null, "public", null, false, false)) {
            while (rs.next()) {
                String indexName = rs.getString("INDEX_NAME");
                if (indexName != null && !indexName.startsWith("pk_") && !indexName.startsWith("sql_")) {
                    indexes.add(indexName);
                }
            }
        }
        return indexes;
    }

    private List<String> queryUniqueConstraints() throws SQLException {
        List<String> constraints = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password)) {
            DatabaseMetaData meta = conn.getMetaData();
            // Get primary keys as a constraint baseline
            for (String table : CORE_TABLES) {
                try (ResultSet rs = meta.getPrimaryKeys(null, "public", table)) {
                    while (rs.next()) {
                        constraints.add("pk_" + table + "_" + rs.getString("COLUMN_NAME"));
                    }
                }
            }
            // Check for unique indexes via pg_indexes
            try (var stmt = conn.createStatement();
                 var rs = stmt.executeQuery(
                         "SELECT indexname, tablename FROM pg_indexes " +
                                 "WHERE schemaname = 'public' AND indexdef LIKE '%UNIQUE%'")) {
                while (rs.next()) {
                    constraints.add("uq_" + rs.getString("tablename") + "_" + rs.getString("indexname"));
                }
            }
        }
        return constraints;
    }

    private boolean hasUniqueOnTable(String table, String column, List<String> constraints) {
        return constraints.stream().anyMatch(c -> c.contains(table) && c.contains(column));
    }
}
