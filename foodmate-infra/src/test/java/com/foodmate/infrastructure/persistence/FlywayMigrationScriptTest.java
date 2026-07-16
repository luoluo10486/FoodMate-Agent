package com.foodmate.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 校验首版 Flyway 迁移脚本的表结构、索引和数据库注释。
 */
class FlywayMigrationScriptTest {
    @Test
    void runtimeMigrationDefinesDurableIdempotencyAndInboxTables() throws IOException {
        String sql = Files.readString(Path.of("src", "main", "resources", "db", "migration", "V2__runtime_inbox.sql"));
        assertTrue(sql.contains("CREATE TABLE runtime_runs"));
        assertTrue(sql.contains("CREATE TABLE runtime_dispatches"));
        assertTrue(sql.contains("CREATE TABLE runtime_cancels"));
        assertTrue(sql.contains("CREATE TABLE runtime_event_inbox"));
        assertTrue(sql.contains("PRIMARY KEY (run_id, event_id)"));
        assertTrue(sql.contains("UNIQUE (run_id, event_seq)"));
    }
    private static final Path INIT_SCHEMA = Path.of(
            "src",
            "main",
            "resources",
            "db",
            "migration",
            "V1__init_core_schema.sql"
    );

    private static final Path ROLLBACK_SCHEMA = Path.of(
            "src",
            "main",
            "resources",
            "db",
            "rollback",
            "R1__drop_core_schema.sql"
    );

    private static final List<String> CORE_TABLES = List.of(
            "users",
            "user_profiles",
            "auth_refresh_tokens",
            "user_avatar_assets",
            "sessions",
            "messages",
            "agent_runs",
            "tool_calls",
            "food_logs",
            "analysis_reports",
            "meal_plans",
            "shopping_lists",
            "user_memories",
            "session_summaries",
            "knowledge_documents",
            "knowledge_chunks",
            "data_sources",
            "schema_catalogs",
            "sql_query_audits",
            "tool_registries",
            "tool_schema_versions",
            "model_usage_logs",
            "model_route_rules",
            "operation_audits"
    );

    private static final List<String> TENANT_SCOPED_TABLES = List.of(
            "users",
            "sessions",
            "knowledge_documents",
            "model_route_rules"
    );

    @Test
    void initSchemaDefinesCoreTablesWithSoftDeleteColumns() throws IOException {
        String sql = Files.readString(INIT_SCHEMA);

        for (String table : CORE_TABLES) {
            String tableBlock = tableBlock(sql, table);
            assertTrue(
                    tableBlock.contains("is_deleted BOOLEAN NOT NULL DEFAULT FALSE"),
                    table + " must include the soft delete flag"
            );
            assertTrue(tableBlock.contains("deleted_at TIMESTAMPTZ"), table + " must include deleted_at");
            assertTrue(tableBlock.contains("deleted_by BIGINT"), table + " must include deleted_by");
        }
    }

    @Test
    void initSchemaDefinesBaselineIndexes() throws IOException {
        String sql = Files.readString(INIT_SCHEMA);

        assertTrue(sql.contains("idx_sessions_user_last_message_at"));
        assertTrue(sql.contains("idx_messages_session_sequence"));
        assertTrue(sql.contains("idx_agent_runs_session_created_at"));
        assertTrue(sql.contains("idx_tool_calls_run_created_at"));
        assertTrue(sql.contains("idx_food_logs_user_meal_time"));
        assertTrue(sql.contains("idx_knowledge_documents_tenant_status"));
    }

    @Test
    void initSchemaDefaultsTenantScopedTablesToSingleTenant() throws IOException {
        String sql = Files.readString(INIT_SCHEMA);

        for (String table : TENANT_SCOPED_TABLES) {
            String tableBlock = tableBlock(sql, table);
            assertTrue(
                    tableBlock.contains("tenant_id BIGINT NOT NULL DEFAULT 0"),
                    table + " must default to the single-tenant placeholder"
            );
        }
    }

    @Test
    void initSchemaDefinesDefaultToolTimeout() throws IOException {
        String sql = Files.readString(INIT_SCHEMA);
        String tableBlock = tableBlock(sql, "tool_schema_versions");

        assertTrue(
                tableBlock.contains("timeout_ms INT NOT NULL DEFAULT 5000"),
                "tool schema versions must default tool execution timeout to 5000 ms"
        );
    }

    @Test
    void initSchemaDocumentsEveryCoreTableAndColumn() throws IOException {
        String sql = Files.readString(INIT_SCHEMA);

        for (String table : CORE_TABLES) {
            String tableCommentPrefix = "COMMENT ON TABLE " + table + " IS";
            assertTrue(sql.contains(tableCommentPrefix), table + " must have a table comment");
            assertTrue(hasChineseComment(sql, tableCommentPrefix), table + " must have a Chinese table comment");

            for (String column : columnsIn(sql, table)) {
                String columnCommentPrefix = "COMMENT ON COLUMN " + table + "." + column + " IS";
                assertTrue(
                        sql.contains(columnCommentPrefix),
                        table + "." + column + " must have a column comment"
                );
                assertTrue(
                        hasChineseComment(sql, columnCommentPrefix),
                        table + "." + column + " must have a Chinese column comment"
                );
            }
        }
    }

    @Test
    void rollbackSchemaDropsEveryCoreTable() throws IOException {
        String rollbackSql = Files.readString(ROLLBACK_SCHEMA);

        assertTrue(rollbackSql.contains("DROP CONSTRAINT IF EXISTS fk_messages_agent_run_id"));
        for (String table : CORE_TABLES) {
            assertTrue(
                    rollbackSql.contains("DROP TABLE IF EXISTS " + table),
                    table + " must be dropped by the rollback script"
            );
        }
    }

    private String tableBlock(String sql, String table) {
        String createTable = "CREATE TABLE " + table + " (";
        int start = sql.indexOf(createTable);
        assertTrue(start >= 0, table + " must be created");
        int end = sql.indexOf("\n);", start);
        assertTrue(end > start, table + " create table block must be closed");
        return sql.substring(start, end);
    }

    private List<String> columnsIn(String sql, String table) {
        return tableBlock(sql, table)
                .lines()
                .skip(1)
                .map(String::trim)
                .takeWhile(line -> !line.startsWith("CONSTRAINT "))
                .filter(line -> !line.isBlank())
                .map(line -> line.split("\\s+", 2)[0].replace(",", ""))
                .toList();
    }

    private boolean hasChineseComment(String sql, String prefix) {
        int start = sql.indexOf(prefix);
        assertTrue(start >= 0, prefix + " must exist");
        int end = sql.indexOf(";", start);
        assertTrue(end > start, prefix + " must end with semicolon");
        return sql.substring(start, end).codePoints().anyMatch(codePoint -> codePoint >= 0x4E00 && codePoint <= 0x9FFF);
    }
}
