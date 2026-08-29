package com.foodmate.infrastructure.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** 在隔离的真实 PostgreSQL 上验证人工迁移和核心业务幂等约束。 */
@Testcontainers
@EnabledIfSystemProperty(named = "docker.available", matches = "true")
class BusinessIdempotencyRealMigrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("foodmate_idempotency_test")
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
    void allManualMigrationsAreExecutableAndIdempotent() {
        Flyway flyway = flywayForAllScripts();

        flyway.migrate();
        var secondRun = flyway.migrate();

        assertEquals(0, secondRun.migrationsExecuted, "全部人工迁移第二次执行必须是 no-op");
    }

    @Test
    void businessWriteIdempotencyIsEnforcedByPostgres() throws SQLException {
        flywayForAllScripts().migrate();
        long userId = nextTestId();
        String suffix = UUID.randomUUID().toString().replace("-", "");

        try (Connection connection = openConnection()) {
            insertUser(connection, userId, "idem_" + suffix, suffix + "@example.com");
            insertFoodLog(connection, userId, nextTestId(), "food-log-" + suffix);
            insertMealPlan(connection, userId, nextTestId(), "meal-plan-" + suffix);
            insertApproval(connection, userId, nextTestId(), "approval-" + suffix);
            insertAudit(connection, userId, "audit-" + suffix);
        }

        assertUniqueViolation(
                () -> {
                    try (Connection connection = openConnection()) {
                        insertFoodLog(connection, userId, nextTestId(), "food-log-" + suffix);
                    }
                },
                "food_logs(user_id,idempotency_key)");
        assertUniqueViolation(
                () -> {
                    try (Connection connection = openConnection()) {
                        insertMealPlan(connection, userId, nextTestId(), "meal-plan-" + suffix);
                    }
                },
                "meal_plans(user_id,idempotency_key)");
        assertUniqueViolation(
                () -> {
                    try (Connection connection = openConnection()) {
                        insertApproval(connection, userId, nextTestId(), "approval-" + suffix);
                    }
                },
                "approval_requests(user_id,idempotency_key)");
        assertUniqueViolation(
                () -> {
                    try (Connection connection = openConnection()) {
                        insertAudit(connection, userId, "audit-" + suffix);
                    }
                },
                "operation_audits(operator_id,idempotency_key)");

        try (Connection connection = openConnection();
                var query =
                        connection.prepareStatement(
                                "SELECT "
                                        + "(SELECT COUNT(*) FROM food_logs WHERE user_id=?),"
                                        + "(SELECT COUNT(*) FROM meal_plans WHERE user_id=?),"
                                        + "(SELECT COUNT(*) FROM approval_requests WHERE user_id=?),"
                                        + "(SELECT COUNT(*) FROM operation_audits WHERE operator_id=?"
                                        + " AND idempotency_key LIKE 'audit-%')")) {
            query.setLong(1, userId);
            query.setLong(2, userId);
            query.setLong(3, userId);
            query.setLong(4, userId);
            try (var rows = query.executeQuery()) {
                assertTrue(rows.next());
                assertEquals(1, rows.getInt(1));
                assertEquals(1, rows.getInt(2));
                assertEquals(1, rows.getInt(3));
                assertEquals(1, rows.getInt(4));
            }
        }
    }

    private Flyway flywayForAllScripts() {
        return Flyway.configure()
                .dataSource(jdbcUrl, username, password)
                .locations(
                        "filesystem:../script/sql/FoodMate/baseline",
                        "filesystem:../script/sql/FoodMate/migration")
                .load();
    }

    private Connection openConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl, username, password);
    }

    private void insertUser(Connection connection, long userId, String userName, String email)
            throws SQLException {
        try (var insert =
                connection.prepareStatement(
                        "INSERT INTO users(user_id,user_no,username,email,password_hash,nickname) "
                                + "VALUES (?,?,?,?,?,?)")) {
            insert.setLong(1, userId);
            insert.setString(2, "IDEM" + userId);
            insert.setString(3, userName);
            insert.setString(4, email);
            insert.setString(5, "test-password-hash");
            insert.setString(6, "idempotency test");
            insert.executeUpdate();
        }
    }

    private void insertFoodLog(Connection connection, long userId, long foodLogId, String key)
            throws SQLException {
        try (var insert =
                connection.prepareStatement(
                        "INSERT INTO food_logs(food_log_id,user_id,meal_time,meal_type,idempotency_key) "
                                + "VALUES (?,?,CURRENT_TIMESTAMP,'breakfast',?)")) {
            insert.setLong(1, foodLogId);
            insert.setLong(2, userId);
            insert.setString(3, key);
            insert.executeUpdate();
        }
    }

    private void insertMealPlan(Connection connection, long userId, long mealPlanId, String key)
            throws SQLException {
        try (var insert =
                connection.prepareStatement(
                        "INSERT INTO meal_plans(meal_plan_id,user_id,plan_name,days,idempotency_key) "
                                + "VALUES (?,?,?,1,?)")) {
            insert.setLong(1, mealPlanId);
            insert.setLong(2, userId);
            insert.setString(3, key);
            insert.setString(4, key);
            insert.executeUpdate();
        }
    }

    private void insertApproval(Connection connection, long userId, long approvalId, String key)
            throws SQLException {
        try (var insert =
                connection.prepareStatement(
                        "INSERT INTO approval_requests(approval_request_id,user_id,operation,"
                                + "parameters_digest,idempotency_key,expires_at) "
                                + "VALUES (?,?,'create','digest-"
                                + approvalId
                                + "',?,"
                                + "CURRENT_TIMESTAMP + INTERVAL '1 hour')")) {
            insert.setLong(1, approvalId);
            insert.setLong(2, userId);
            insert.setString(3, key);
            insert.executeUpdate();
        }
    }

    private void insertAudit(Connection connection, long operatorId, String key)
            throws SQLException {
        try (var insert =
                connection.prepareStatement(
                        "INSERT INTO operation_audits(operation_audit_id,operator_id,target_type,"
                                + "action,result,idempotency_key,parameters_digest) "
                                + "VALUES (?,?,'food_log','food_log.create','success',?,?)")) {
            insert.setLong(1, nextTestId());
            insert.setLong(2, operatorId);
            insert.setString(3, key);
            insert.setString(4, "digest-" + key);
            insert.executeUpdate();
        }
    }

    private void assertUniqueViolation(SqlOperation operation, String constraintDescription) {
        SQLException exception = assertThrows(SQLException.class, operation::run);
        assertEquals("23505", exception.getSQLState(), constraintDescription + " 必须拒绝重复事实");
    }

    private static long nextTestId() {
        return TEST_ID.incrementAndGet();
    }

    @FunctionalInterface
    private interface SqlOperation {
        void run() throws SQLException;
    }

    private static final AtomicLong TEST_ID = new AtomicLong(9_100_000_000_000L);
}
