package com.foodmate.infrastructure.persistence.retention;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.foodmate.application.retention.port.out.DataRetentionDatabasePurgePort.PurgeResult;
import com.foodmate.infrastructure.persistence.retention.adapter.DataRetentionDatabasePurgeAdapter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** 在隔离 PostgreSQL 上验证 M3 数据库清理的真实删除、保留事实和幂等重放。 */
@Testcontainers
@EnabledIfSystemProperty(named = "docker.available", matches = "true")
class DataRetentionDatabasePurgeRealIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("foodmate_retention_test")
                    .withUsername("test")
                    .withPassword("test");

    private static String jdbcUrl;

    @BeforeAll
    static void migrateSchema() {
        jdbcUrl = postgres.getJdbcUrl();
        Flyway.configure()
                .dataSource(jdbcUrl, postgres.getUsername(), postgres.getPassword())
                .locations(
                        "filesystem:../script/sql/FoodMate/baseline",
                        "filesystem:../script/sql/FoodMate/migration")
                .load()
                .migrate();
    }

    @Test
    void knowledgeDocumentPurgeRemovesChildrenAndCanBeReplayed() throws SQLException {
        long userId = 9_300_000_000_001L;
        long documentId = 9_300_000_000_002L;
        long jobId = 9_300_000_000_003L;
        long itemId = 9_300_000_000_004L;
        long requestId = 9_300_000_000_005L;
        long objectTaskId = 9_300_000_000_006L;
        long vectorTaskId = 9_300_000_000_007L;
        long databaseTaskId = 9_300_000_000_008L;
        String suffix = UUID.randomUUID().toString().replace("-", "");

        try (Connection connection = openConnection()) {
            insertFixture(
                    connection,
                    userId,
                    documentId,
                    jobId,
                    itemId,
                    requestId,
                    objectTaskId,
                    vectorTaskId,
                    databaseTaskId,
                    suffix);
        }

        try (SqlSession session = openSession()) {
            DataRetentionDatabasePurgeAdapter adapter =
                    new DataRetentionDatabasePurgeAdapter(
                            session.getMapper(DataRetentionDatabasePurgeMapper.class));

            PurgeResult first = adapter.purgeWithResult("knowledge_document", documentId);
            session.commit();

            assertEquals(new PurgeResult("postgresql", 8, true), first);
        }

        assertCountsAfterPurge(
                documentId, itemId, jobId, requestId, objectTaskId, vectorTaskId, databaseTaskId);

        try (SqlSession session = openSession()) {
            DataRetentionDatabasePurgeAdapter adapter =
                    new DataRetentionDatabasePurgeAdapter(
                            session.getMapper(DataRetentionDatabasePurgeMapper.class));

            PurgeResult replay = adapter.purgeWithResult("knowledge_document", documentId);
            session.commit();

            assertEquals(new PurgeResult("postgresql", 0, true), replay);
        }
    }

    private void insertFixture(
            Connection connection,
            long userId,
            long documentId,
            long jobId,
            long itemId,
            long requestId,
            long objectTaskId,
            long vectorTaskId,
            long databaseTaskId,
            String suffix)
            throws SQLException {
        execute(
                connection,
                "INSERT INTO users(user_id,user_no,username,email,password_hash,nickname,role) VALUES(?,?,?,?,?,?,?)",
                userId,
                "RET" + userId,
                "retention_" + suffix,
                suffix + "@example.com",
                "test-password-hash",
                "retention test",
                "operator");
        execute(
                connection,
                "INSERT INTO knowledge_documents(document_id,source_type,title,status,version,storage_key,created_by,updated_by,is_deleted,deleted_at,deleted_by,source_name,source_version,license_notice,visibility,current_version,revision) VALUES(?,?,?,'indexed','v1',?,?,?,TRUE,CURRENT_TIMESTAMP,?,?,?,'test license','deleted',TRUE,1)",
                documentId,
                "markdown",
                "retention-guide-" + suffix,
                "knowledge/public/" + documentId + "/guide.md",
                userId,
                userId,
                userId,
                "retention-source-" + suffix,
                "v1");
        execute(
                connection,
                "INSERT INTO knowledge_import_jobs(job_id,operator_id,idempotency_key,requested_mode,source_type,source_name,source_version,license_notice,status) VALUES(?,?,?,'stub','markdown',?,'v1','test license','completed')",
                jobId,
                userId,
                "job-" + suffix,
                "retention-source-" + suffix);
        execute(
                connection,
                "INSERT INTO knowledge_import_items(item_id,job_id,document_id,filename,content_type,file_size,upload_status,index_status,attempt_count,chunk_count) VALUES(?,?,?,?,?,1,'uploaded','indexed',1,1)",
                itemId,
                jobId,
                documentId,
                "guide.md",
                "text/markdown");
        execute(
                connection,
                "INSERT INTO knowledge_chunks(chunk_id,document_id,chunk_no,chunk_text,version,document_version,embedding_id,acl_metadata) VALUES(?,?,0,'safe test text','v1','v1',?,CAST('{\"tenant_id\":0,\"scope\":\"public_published\"}' AS jsonb))",
                9_300_000_000_009L,
                documentId,
                "embedding-" + suffix);
        execute(
                connection,
                "INSERT INTO knowledge_index_outbox(outbox_id,item_id,topic,payload_json) VALUES(?,?,?,'{}'::jsonb)",
                9_300_000_000_010L,
                itemId,
                "foodmate-knowledge-index-v1");
        execute(
                connection,
                "INSERT INTO knowledge_import_sse_outbox(event_id,job_id,item_id,event_type,payload_json) VALUES(?,?,?,'knowledge.index.indexed','{}'::jsonb)",
                9_300_000_000_011L,
                jobId,
                itemId);
        execute(
                connection,
                "INSERT INTO knowledge_visibility_outbox(outbox_id,document_id,topic,payload_json) VALUES(?,?,?,'{}'::jsonb)",
                9_300_000_000_012L,
                documentId,
                "foodmate-knowledge-visibility-v1");
        execute(
                connection,
                "INSERT INTO knowledge_index_result_inbox(item_id,document_version,attempt_count,payload_hash) VALUES(?,?,1,?)",
                itemId,
                "v1",
                "hash-" + suffix);
        execute(
                connection,
                "UPDATE data_retention_policies SET hard_delete_enabled=TRUE WHERE resource_type='knowledge_document'");
        execute(
                connection,
                "INSERT INTO data_purge_requests(request_id,resource_type,resource_id,policy_id,requested_by,idempotency_key,status,deleted_at_snapshot,eligible_at,approved_by,approved_at) VALUES(?,?,?,250001,?,?, 'approved',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,?,CURRENT_TIMESTAMP)",
                requestId,
                "knowledge_document",
                documentId,
                userId,
                "purge-" + suffix,
                userId);
        execute(
                connection,
                "INSERT INTO data_purge_tasks(task_id,request_id,task_type,target_ref,status) VALUES(?,?,?,'{}'::jsonb,'pending')",
                objectTaskId,
                requestId,
                "object_storage");
        execute(
                connection,
                "INSERT INTO data_purge_tasks(task_id,request_id,task_type,topic,target_ref,status) VALUES(?,?,?,'foodmate-knowledge-purge-v1','{}'::jsonb,'pending')",
                vectorTaskId,
                requestId,
                "vector_index");
        execute(
                connection,
                "INSERT INTO data_purge_tasks(task_id,request_id,task_type,target_ref,status) VALUES(?,?,?,'{}'::jsonb,'pending')",
                databaseTaskId,
                requestId,
                "database");
        execute(
                connection,
                "INSERT INTO data_purge_task_results(result_id,task_id,request_id,resource_type,resource_id,task_type,version,status,backend,deleted_count,verified_absent,result_digest) VALUES(?,?,?,?,?,?,'v1','succeeded','minio',1,TRUE,?)",
                9_300_000_000_013L,
                objectTaskId,
                requestId,
                "knowledge_document",
                documentId,
                "object_storage",
                "a".repeat(64));
    }

    private void assertCountsAfterPurge(
            long documentId,
            long itemId,
            long jobId,
            long requestId,
            long objectTaskId,
            long vectorTaskId,
            long databaseTaskId)
            throws SQLException {
        try (Connection connection = openConnection();
                PreparedStatement query =
                        connection.prepareStatement(
                                "SELECT (SELECT COUNT(*) FROM knowledge_documents WHERE document_id=?),"
                                        + "(SELECT COUNT(*) FROM knowledge_chunks WHERE document_id=?),"
                                        + "(SELECT COUNT(*) FROM knowledge_import_items WHERE item_id=?),"
                                        + "(SELECT COUNT(*) FROM knowledge_import_jobs WHERE job_id=?),"
                                        + "(SELECT COUNT(*) FROM knowledge_index_outbox WHERE item_id=?),"
                                        + "(SELECT COUNT(*) FROM knowledge_index_result_inbox WHERE item_id=?),"
                                        + "(SELECT COUNT(*) FROM knowledge_import_sse_outbox WHERE item_id=?),"
                                        + "(SELECT COUNT(*) FROM knowledge_visibility_outbox WHERE document_id=?),"
                                        + "(SELECT COUNT(*) FROM data_purge_task_results WHERE task_id IN (?,?,?)),"
                                        + "(SELECT COUNT(*) FROM data_purge_tasks WHERE request_id=?),"
                                        + "(SELECT COUNT(*) FROM data_purge_requests WHERE request_id=?)")) {
            query.setLong(1, documentId);
            query.setLong(2, documentId);
            query.setLong(3, itemId);
            query.setLong(4, jobId);
            query.setLong(5, itemId);
            query.setLong(6, itemId);
            query.setLong(7, itemId);
            query.setLong(8, documentId);
            query.setLong(9, objectTaskId);
            query.setLong(10, vectorTaskId);
            query.setLong(11, databaseTaskId);
            query.setLong(12, requestId);
            query.setLong(13, requestId);
            try (var rows = query.executeQuery()) {
                rows.next();
                assertEquals(0, rows.getInt(1));
                assertEquals(0, rows.getInt(2));
                assertEquals(0, rows.getInt(3));
                assertEquals(0, rows.getInt(4));
                assertEquals(0, rows.getInt(5));
                assertEquals(0, rows.getInt(6));
                assertEquals(0, rows.getInt(7));
                assertEquals(0, rows.getInt(8));
                assertEquals(1, rows.getInt(9));
                assertEquals(3, rows.getInt(10));
                assertEquals(1, rows.getInt(11));
            }
        }
    }

    private SqlSession openSession() {
        UnpooledDataSource dataSource =
                new UnpooledDataSource(
                        "org.postgresql.Driver",
                        jdbcUrl,
                        postgres.getUsername(),
                        postgres.getPassword());
        Environment environment =
                new Environment("retention-test", new JdbcTransactionFactory(), dataSource);
        Configuration configuration = new Configuration(environment);
        configuration.addMapper(DataRetentionDatabasePurgeMapper.class);
        SqlSessionFactory factory = new SqlSessionFactoryBuilder().build(configuration);
        return factory.openSession(false);
    }

    private Connection openConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl, postgres.getUsername(), postgres.getPassword());
    }

    private void execute(Connection connection, String sql, Object... values) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < values.length; index++) {
                statement.setObject(index + 1, values[index]);
            }
            statement.executeUpdate();
        }
    }
}
