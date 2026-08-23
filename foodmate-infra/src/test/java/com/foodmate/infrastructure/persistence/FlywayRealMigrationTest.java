package com.foodmate.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * 在真实 PostgreSQL 实例上执行 Flyway 迁移验证。
 *
 * <p>P0-3: 确保迁移脚本可在 PostgreSQL 中执行， 所有核心表已创建，约束和索引存在，重复执行幂等。
 *
 * <p>需要 Docker 环境。本地开发时通过 -Ddocker.available=true 启用。
 */
@Testcontainers
@EnabledIfSystemProperty(named = "docker.available", matches = "true")
class FlywayRealMigrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
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
        Flyway flyway =
                Flyway.configure()
                        .dataSource(jdbcUrl, username, password)
                        .locations("filesystem:../script/sql/FoodMate/baseline")
                        .load();
        flyway.migrate();

        // Assert: all application tables exist. Flyway's metadata table is excluded.
        List<String> actualTables = queryTables();
        for (String table : CORE_TABLES) {
            assertTrue(
                    actualTables.contains(table),
                    "Core table '" + table + "' must be created by Flyway migration");
        }
        assertEquals(CORE_TABLES.size(), actualTables.size());
    }

    @Test
    void flywayMigrateIsIdempotent() {
        // Arrange: first migration
        Flyway flyway =
                Flyway.configure()
                        .dataSource(jdbcUrl, username, password)
                        .locations("filesystem:../script/sql/FoodMate/baseline")
                        .load();
        flyway.migrate();

        // Act: second migrate — should be a no-op
        assertDoesNotThrow(
                () -> {
                    var result = flyway.migrate();
                    assertEquals(
                            0,
                            result.migrationsExecuted,
                            "Second migrate should execute 0 migrations (idempotent)");
                });
    }

    @Test
    void flywayValidateSucceeds() {
        Flyway flyway =
                Flyway.configure()
                        .dataSource(jdbcUrl, username, password)
                        .locations("filesystem:../script/sql/FoodMate/baseline")
                        .load();
        flyway.migrate();

        assertDoesNotThrow(flyway::validate, "Flyway validate must pass after a clean migration");
    }

    @Test
    void schemaHasRequiredIndexes() throws SQLException {
        Flyway flyway =
                Flyway.configure()
                        .dataSource(jdbcUrl, username, password)
                        .locations("filesystem:../script/sql/FoodMate/baseline")
                        .load();
        flyway.migrate();

        List<String> indexes = queryIndexes();

        assertTrue(indexes.contains("idx_sessions_user_last_message_at"), indexes.toString());
        assertTrue(indexes.contains("idx_messages_session_sequence"), indexes.toString());
        assertTrue(indexes.contains("idx_agent_runs_session_created_at"), indexes.toString());
        assertTrue(indexes.contains("idx_tool_calls_run_created_at"), indexes.toString());
        assertTrue(indexes.contains("idx_food_logs_user_meal_time"), indexes.toString());
        assertTrue(indexes.contains("idx_knowledge_documents_tenant_status"), indexes.toString());
    }

    @Test
    void databaseEnforcesActiveAccountUniquenessAndAllowsSoftDeletedReuse() throws SQLException {
        migrateSchema();
        long firstUserId = nextTestId();
        String username = "db_user_" + UUID.randomUUID().toString().replace("-", "");
        String email = username + "@example.com";
        try (Connection connection = openConnection()) {
            insertUser(connection, firstUserId, username, email);

            SQLException duplicateUsername =
                    assertThrows(
                            SQLException.class,
                            () -> insertUser(connection, nextTestId(), username, "other-" + email));
            assertEquals("23505", duplicateUsername.getSQLState());

            SQLException duplicateEmail =
                    assertThrows(
                            SQLException.class,
                            () -> insertUser(connection, nextTestId(), "other_" + username, email));
            assertEquals("23505", duplicateEmail.getSQLState());

            try (var update =
                    connection.prepareStatement(
                            "UPDATE users SET is_deleted=TRUE,deleted_at=CURRENT_TIMESTAMP WHERE user_id=?")) {
                update.setLong(1, firstUserId);
                assertEquals(1, update.executeUpdate());
            }
            assertDoesNotThrow(
                    () -> insertUser(connection, nextTestId(), username, email),
                    "逻辑删除后的账号应允许按唯一索引复用用户名和邮箱");
        }
    }

    @Test
    void databaseRevocationRemovesAuthenticationSessionFromActiveSet() throws SQLException {
        migrateSchema();
        long userId = nextTestId();
        long authSessionId = nextTestId();
        try (Connection connection = openConnection()) {
            insertUser(
                    connection,
                    userId,
                    "revoke_" + UUID.randomUUID().toString().replace("-", ""),
                    UUID.randomUUID() + "@example.com");
            try (var insert =
                    connection.prepareStatement(
                            "INSERT INTO user_auth_sessions(auth_session_id,user_id,session_token_hash,csrf_token_hash,expires_at,created_by) VALUES (?,?,?,?,CURRENT_TIMESTAMP+INTERVAL '1 day',?)")) {
                insert.setLong(1, authSessionId);
                insert.setLong(2, userId);
                insert.setString(3, "session-hash-" + authSessionId);
                insert.setString(4, "csrf-hash-" + authSessionId);
                insert.setLong(5, userId);
                insert.executeUpdate();
            }
            try (var revoke =
                    connection.prepareStatement(
                            "UPDATE user_auth_sessions SET revoked_at=CURRENT_TIMESTAMP WHERE user_id=? AND revoked_at IS NULL")) {
                revoke.setLong(1, userId);
                assertEquals(1, revoke.executeUpdate());
            }
            try (var active =
                            connection.prepareStatement(
                                    "SELECT COUNT(*) FROM user_auth_sessions WHERE user_id=? AND revoked_at IS NULL AND is_deleted=FALSE");
                    var revoked =
                            connection.prepareStatement(
                                    "SELECT revoked_at IS NOT NULL FROM user_auth_sessions WHERE auth_session_id=?")) {
                active.setLong(1, userId);
                revoked.setLong(1, authSessionId);
                try (ResultSet activeRows = active.executeQuery()) {
                    activeRows.next();
                    assertEquals(0, activeRows.getInt(1));
                }
                try (ResultSet revokedRow = revoked.executeQuery()) {
                    revokedRow.next();
                    assertTrue(revokedRow.getBoolean(1));
                }
            }
        }
    }

    @Test
    void databaseSerializesMessageSequenceAllocationAcrossTransactions() throws Exception {
        migrateSchema();
        long userId = nextTestId();
        long sessionId = nextTestId();
        try (Connection setup = openConnection()) {
            insertUser(
                    setup,
                    userId,
                    "sequence_" + UUID.randomUUID().toString().replace("-", ""),
                    UUID.randomUUID() + "@example.com");
            try (var insert =
                    setup.prepareStatement(
                            "INSERT INTO sessions(session_id,user_id,title,mode) VALUES (?,?,?, 'chat')")) {
                insert.setLong(1, sessionId);
                insert.setLong(2, userId);
                insert.setString(3, "sequence test");
                insert.executeUpdate();
            }
        }

        int workers = 2;
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        try {
            List<Future<Integer>> results =
                    java.util.stream.IntStream.range(0, workers)
                            .mapToObj(
                                    ignored ->
                                            executor.submit(
                                                    () ->
                                                            insertMessageWithLockedSequence(
                                                                    sessionId, userId, ready,
                                                                    start)))
                            .toList();
            assertTrue(ready.await(5, TimeUnit.SECONDS), "两个事务都应进入并发屏障");
            start.countDown();
            List<Integer> sequences = results.stream().map(this::getResult).sorted().toList();
            assertEquals(List.of(1, 2), sequences);
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
        try (Connection connection = openConnection();
                var query =
                        connection.prepareStatement(
                                "SELECT COUNT(*),MAX(sequence_no) FROM messages WHERE session_id=? AND is_deleted=FALSE")) {
            query.setLong(1, sessionId);
            try (ResultSet rows = query.executeQuery()) {
                rows.next();
                assertEquals(2, rows.getInt(1));
                assertEquals(2, rows.getInt(2));
            }
        }
    }

    // ── Core tables list ─────────────────────────────────────────────────

    private static final List<String> CORE_TABLES =
            List.of(
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
                    "operation_audits",
                    "runtime_runs",
                    "runtime_dispatches",
                    "runtime_cancels",
                    "runtime_event_inbox",
                    "user_auth_sessions");

    // ── Query helpers ───────────────────────────────────────────────────

    private List<String> queryTables() {
        List<String> tables = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password);
                ResultSet rs =
                        conn.getMetaData()
                                .getTables(null, "public", null, new String[] {"TABLE"})) {
            while (rs.next()) {
                String tableName = rs.getString("TABLE_NAME");
                if (!"flyway_schema_history".equals(tableName)) {
                    tables.add(tableName);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return tables;
    }

    private List<String> queryIndexes() throws SQLException {
        List<String> indexes = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password);
                var statement = conn.createStatement();
                ResultSet rs =
                        statement.executeQuery(
                                "SELECT indexname FROM pg_indexes WHERE schemaname = 'public'")) {
            while (rs.next()) {
                String indexName = rs.getString("indexname");
                if (indexName != null
                        && !indexName.startsWith("pk_")
                        && !indexName.contains("_pkey")) {
                    indexes.add(indexName);
                }
            }
        }
        return indexes;
    }

    private void migrateSchema() {
        Flyway.configure()
                .dataSource(jdbcUrl, username, password)
                .locations("filesystem:../script/sql/FoodMate/baseline")
                .load()
                .migrate();
    }

    private Connection openConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl, username, password);
    }

    private void insertUser(Connection connection, long userId, String username, String email)
            throws SQLException {
        try (var insert =
                connection.prepareStatement(
                        "INSERT INTO users(user_id,user_no,username,email,password_hash,nickname) VALUES (?,?,?,?,?,?)")) {
            insert.setLong(1, userId);
            insert.setString(2, "DB" + userId);
            insert.setString(3, username);
            insert.setString(4, email);
            insert.setString(5, "test-password-hash");
            insert.setString(6, "database test");
            insert.executeUpdate();
        }
    }

    private int insertMessageWithLockedSequence(
            long sessionId, long userId, CountDownLatch ready, CountDownLatch start) {
        try (Connection connection = openConnection()) {
            connection.setAutoCommit(false);
            ready.countDown();
            assertTrue(start.await(5, TimeUnit.SECONDS), "并发事务应收到开始信号");
            try (var lock =
                    connection.prepareStatement(
                            "WITH advisory_lock AS (SELECT pg_advisory_xact_lock(?)) SELECT 0 FROM advisory_lock")) {
                lock.setLong(1, sessionId);
                lock.executeQuery().close();
            }
            int sequence;
            try (var next =
                    connection.prepareStatement(
                            "SELECT COALESCE(MAX(sequence_no),0)+1 FROM messages WHERE session_id=? AND is_deleted=FALSE")) {
                next.setLong(1, sessionId);
                try (ResultSet row = next.executeQuery()) {
                    row.next();
                    sequence = row.getInt(1);
                }
            }
            try (var insert =
                    connection.prepareStatement(
                            "INSERT INTO messages(message_id,session_id,role,content,sequence_no,created_by) VALUES (?,?, 'user',?,?,?)")) {
                insert.setLong(1, nextTestId());
                insert.setLong(2, sessionId);
                insert.setString(3, "concurrent message");
                insert.setInt(4, sequence);
                insert.setLong(5, userId);
                insert.executeUpdate();
            }
            connection.commit();
            return sequence;
        } catch (Exception exception) {
            throw new IllegalStateException("数据库消息序号并发测试失败", exception);
        }
    }

    private int getResult(Future<Integer> result) {
        try {
            return result.get(10, TimeUnit.SECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待数据库并发测试结果被中断", exception);
        } catch (ExecutionException | java.util.concurrent.TimeoutException exception) {
            throw new IllegalStateException("数据库并发测试未收敛", exception);
        }
    }

    private static long nextTestId() {
        return TEST_ID.incrementAndGet();
    }

    private static final AtomicLong TEST_ID = new AtomicLong(9_000_000_000_000L);
}
