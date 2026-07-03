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
    private static final Path INIT_SCHEMA = Path.of(
            "src",
            "main",
            "resources",
            "db",
            "migration",
            "V1__init_core_schema.sql"
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
