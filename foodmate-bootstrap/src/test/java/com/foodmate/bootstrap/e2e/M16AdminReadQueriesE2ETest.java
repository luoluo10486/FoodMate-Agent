package com.foodmate.bootstrap.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodmate.shared.id.IdGenerator;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/** Admin 只读投影的本地真实 HTTP 验证；运行时需显式传入 {@code foodmate.local-e2e=true}。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(
        properties = {
            "foodmate.storage.endpoint=http://localhost:9000",
            "foodmate.storage.access-key=foodmate-local",
            "foodmate.storage.secret-key=foodmate-local-secret-change-me-20260722",
            "foodmate.storage.bucket=foodmate-private"
        })
@EnabledIfSystemProperty(named = "foodmate.local-e2e", matches = "true")
class M16AdminReadQueriesE2ETest {
    private static final String RAW_SQL = "SELECT 'm16-secret-raw-sql'";
    private static final String RAW_PAYLOAD = "m16-secret-raw-payload";
    private static final String STORAGE_KEY = "m16-private-storage-key";

    @LocalServerPort int port;
    @Autowired TestRestTemplate rest;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired IdGenerator ids;

    private final Set<Long> userIds = new HashSet<>();
    private final Set<Long> sessionIds = new HashSet<>();
    private final Set<Long> runIds = new HashSet<>();
    private final Set<Long> toolCallIds = new HashSet<>();
    private final Set<Long> sqlAuditIds = new HashSet<>();
    private final Set<Long> toolIds = new HashSet<>();
    private final Set<Long> usageIds = new HashSet<>();
    private final Set<Long> documentIds = new HashSet<>();
    private final Set<Long> chunkIds = new HashSet<>();
    private final Set<Long> eventIds = new HashSet<>();
    private final Set<Long> sseOutboxIds = new HashSet<>();
    private final Set<Long> operationAuditIds = new HashSet<>();
    private final Set<Long> dlqIds = new HashSet<>();

    @AfterEach
    void cleanTemporaryFacts() {
        for (long dlqId : dlqIds) {
            jdbc.update("DELETE FROM runtime_dlq_replay_outbox WHERE dlq_id=?", dlqId);
            jdbc.update("DELETE FROM runtime_message_dlq WHERE dlq_id=?", dlqId);
        }
        for (long sseOutboxId : sseOutboxIds) {
            jdbc.update(
                    "DELETE FROM agent_run_sse_outbox WHERE agent_run_sse_outbox_id=?",
                    sseOutboxId);
        }
        for (long eventId : eventIds) {
            jdbc.update(
                    "DELETE FROM runtime_event_inbox_v2 WHERE runtime_event_inbox_id=?", eventId);
        }
        for (long operationAuditId : operationAuditIds) {
            jdbc.update(
                    "DELETE FROM operation_audits WHERE operation_audit_id=?", operationAuditId);
        }
        for (long sqlAuditId : sqlAuditIds) {
            jdbc.update("DELETE FROM sql_query_audits WHERE sql_audit_id=?", sqlAuditId);
        }
        for (long toolCallId : toolCallIds) {
            jdbc.update("DELETE FROM tool_calls WHERE tool_call_id=?", toolCallId);
        }
        for (long usageId : usageIds) {
            jdbc.update("DELETE FROM model_usage_logs WHERE model_usage_log_id=?", usageId);
        }
        for (long runId : runIds) {
            jdbc.update("DELETE FROM agent_runs WHERE agent_run_id=?", runId);
        }
        for (long chunkId : chunkIds) {
            jdbc.update("DELETE FROM knowledge_chunks WHERE chunk_id=?", chunkId);
        }
        for (long documentId : documentIds) {
            jdbc.update("DELETE FROM knowledge_visibility_outbox WHERE document_id=?", documentId);
            jdbc.update("DELETE FROM knowledge_import_items WHERE document_id=?", documentId);
            jdbc.update("DELETE FROM knowledge_documents WHERE document_id=?", documentId);
        }
        for (long toolId : toolIds) {
            jdbc.update("DELETE FROM tool_schema_versions WHERE tool_id=?", toolId);
            jdbc.update("DELETE FROM tool_registries WHERE tool_id=?", toolId);
        }
        for (long sessionId : sessionIds) {
            jdbc.update("DELETE FROM session_summaries WHERE session_id=?", sessionId);
            jdbc.update("DELETE FROM messages WHERE session_id=?", sessionId);
            jdbc.update("DELETE FROM sessions WHERE session_id=?", sessionId);
        }
        for (long userId : userIds) {
            if (tableExists("agent_feedback")) {
                jdbc.update("DELETE FROM agent_feedback WHERE user_id=?", userId);
            }
            if (tableExists("approval_requests")) {
                jdbc.update("DELETE FROM approval_requests WHERE user_id=?", userId);
            }
            jdbc.update("DELETE FROM password_reset_tokens WHERE user_id=?", userId);
            jdbc.update("DELETE FROM data_export_jobs WHERE user_id=?", userId);
            jdbc.update("DELETE FROM account_deletion_jobs WHERE user_id=?", userId);
            jdbc.update("DELETE FROM user_memories WHERE user_id=?", userId);
            jdbc.update("DELETE FROM user_avatar_assets WHERE user_id=?", userId);
            jdbc.update("DELETE FROM user_profiles WHERE user_id=?", userId);
            jdbc.update("DELETE FROM auth_refresh_tokens WHERE user_id=?", userId);
            jdbc.update("DELETE FROM user_auth_sessions WHERE user_id=?", userId);
            jdbc.update("DELETE FROM users WHERE user_id=?", userId);
        }
    }

    @Test
    void adminCanReadAllProjectionsWithFiltersAndWithoutSensitivePayloads() throws Exception {
        String suffix = suffix();
        String adminName = "m16_admin_" + suffix;
        String ordinaryName = "m16_user_" + suffix;
        long adminId = register(adminName);
        long ordinaryId = register(ordinaryName);
        userIds.add(adminId);
        userIds.add(ordinaryId);
        jdbc.update("UPDATE users SET role='admin' WHERE user_id=?", adminId);

        HttpHeaders admin = login(adminName);
        HttpHeaders ordinary = login(ordinaryName);
        Facts facts = insertFacts(adminId, ordinaryId, suffix);

        ResponseEntity<String> dashboard = get("/api/admin/dashboard", admin);
        assertEquals(200, dashboard.getStatusCode().value(), dashboard.getBody());
        String dashboardBody = body(dashboard);
        assertTrue(dashboardBody.contains(facts.traceId()));
        assertTrue(dashboardBody.contains(facts.toolName()));
        assertFalse(dashboardBody.contains(RAW_SQL));
        assertFalse(dashboardBody.contains(RAW_PAYLOAD));
        assertFalse(dashboardBody.contains(STORAGE_KEY));

        assertPageContains(
                "/api/admin/queries/users?query=" + facts.username() + "&page=1&size=1",
                admin,
                "users",
                facts.username());
        assertPageContains(
                "/api/admin/queries/runs?query=" + facts.traceId() + "&page=1&size=1",
                admin,
                "runs",
                facts.traceId());
        assertPageContains(
                "/api/admin/queries/traces?query=" + facts.traceId() + "&page=1&size=1",
                admin,
                "traces",
                facts.traceId());
        assertPageContains(
                "/api/admin/queries/tool-calls?query=" + facts.toolName() + "&page=1&size=1",
                admin,
                "tool-calls",
                facts.toolName());
        JsonNode sqlAudits =
                page(
                        "/api/admin/queries/sql-audits?query=" + facts.traceId() + "&page=1&size=1",
                        admin,
                        "sql-audits");
        assertEquals(1, sqlAudits.path("items").size());
        assertTrue(sqlAudits.path("items").get(0).path("query_hash").isTextual());
        assertFalse(sqlAudits.toString().contains(RAW_SQL));

        assertPageContains(
                "/api/admin/queries/tools?query=" + facts.toolName() + "&page=1&size=1",
                admin,
                "tools",
                facts.toolName());
        assertPageContains(
                "/api/admin/queries/usage?query=" + facts.providerCode() + "&page=1&size=1",
                admin,
                "usage",
                facts.providerCode());
        assertPageContains(
                "/api/admin/queries/knowledge?query=" + facts.documentTitle() + "&page=1&size=1",
                admin,
                "knowledge",
                facts.documentTitle());

        JsonNode deleted =
                page(
                        "/api/admin/queries/deleted?resource_type=knowledge_document&page=1&size=100",
                        admin,
                        "deleted");
        assertTrue(containsLong(deleted.path("items"), "resource_id", facts.deletedDocumentId()));

        JsonNode audits =
                page(
                        "/api/admin/queries/operation-audits?action="
                                + facts.action()
                                + "&page=1&size=1",
                        admin,
                        "operation-audits");
        assertEquals(1, audits.path("items").size());
        assertEquals(facts.action(), audits.path("items").get(0).path("action").asText());
        assertFalse(audits.toString().contains(RAW_PAYLOAD));

        assertPageContains(
                "/api/admin/queries/dlq?query=" + facts.consumerGroup() + "&page=1&size=1",
                admin,
                "dlq",
                facts.consumerGroup());

        JsonNode traceDetail = data(get("/api/admin/queries/traces/" + facts.traceId(), admin));
        assertEquals(facts.traceId(), traceDetail.path("summary").path("trace_id").asText());
        assertTrue(traceDetail.path("spans").size() >= 5);
        assertFalse(traceDetail.toString().contains(RAW_SQL));
        assertFalse(traceDetail.toString().contains(RAW_PAYLOAD));
        assertFalse(traceDetail.toString().contains(STORAGE_KEY));

        JsonNode report = data(get("/api/admin/audit-reports/current", admin));
        assertTrue(report.path("generated_at").isTextual());
        assertTrue(report.path("checks").isArray());
        assertTrue(report.path("checks").size() > 0);

        for (String path : adminReadPaths(facts)) {
            ResponseEntity<String> forbidden = get(path, ordinary);
            assertEquals(403, forbidden.getStatusCode().value(), path + " " + forbidden.getBody());
        }
    }

    private Facts insertFacts(long adminId, long ordinaryId, String suffix) {
        long sessionId = ids.nextId();
        sessionIds.add(sessionId);
        jdbc.update(
                "INSERT INTO sessions(session_id,user_id,title,mode,created_by,updated_by) VALUES (?,?,'m16 session','agent',?,?)",
                sessionId,
                ordinaryId,
                ordinaryId,
                ordinaryId);

        long runId = ids.nextId();
        runIds.add(runId);
        String traceId = "m16-trace-" + suffix;
        String intent = "m16-intent-" + suffix;
        jdbc.update(
                "INSERT INTO agent_runs(agent_run_id,session_id,intent,status,plan_json,result_json,trace_id,created_by,updated_by,result_type) VALUES (?, ?, ?, 'completed', '{}'::jsonb, '{}'::jsonb, ?, ?, ?, 'normal')",
                runId,
                sessionId,
                intent,
                traceId,
                ordinaryId,
                ordinaryId);

        String toolName = "m16-tool-" + suffix;
        long toolCallId = ids.nextId();
        toolCallIds.add(toolCallId);
        jdbc.update(
                "INSERT INTO tool_calls(tool_call_id,agent_run_id,tool_name,status,latency_ms,trace_id,created_by,updated_by) VALUES (?,? ,?,'success',37,?,?,?)",
                toolCallId,
                runId,
                toolName,
                traceId,
                ordinaryId,
                ordinaryId);

        long sqlAuditId = ids.nextId();
        sqlAuditIds.add(sqlAuditId);
        jdbc.update(
                "INSERT INTO sql_query_audits(sql_audit_id,session_id,agent_run_id,original_question,sql_text,status,row_count,latency_ms,reject_reason,trace_id,created_by,updated_by) VALUES (?,?,?,?,?,'executed',2,19,NULL,?,?,?)",
                sqlAuditId,
                sessionId,
                runId,
                "m16 question " + suffix,
                RAW_SQL,
                traceId,
                ordinaryId,
                ordinaryId);

        long toolId = ids.nextId();
        toolIds.add(toolId);
        jdbc.update(
                "INSERT INTO tool_registries(tool_id,name,status,current_version,risk_level,availability_scope,category,created_by,updated_by,revision) VALUES (?,?, 'active','1.0.0','low','user','m16',?,?,1)",
                toolId,
                toolName,
                adminId,
                adminId);

        long usageId = ids.nextId();
        usageIds.add(usageId);
        jdbc.update(
                "INSERT INTO model_usage_logs(model_usage_log_id,request_id,trace_id,scene,provider_code,model_name,usage_json,latency_ms,cost_amount,status,created_by,updated_by) VALUES (?,?,?,?,?,?,?::jsonb,?,?, 'success',?,?)",
                usageId,
                "m16-request-" + suffix,
                traceId,
                "m16-scene",
                "m16-provider-" + suffix,
                "m16-model",
                "{\"total_tokens\":\"123\"}",
                42,
                new BigDecimal("0.123456"),
                ordinaryId,
                ordinaryId);

        long documentId = ids.nextId();
        documentIds.add(documentId);
        String documentTitle = "m16-document-" + suffix;
        jdbc.update(
                "INSERT INTO knowledge_documents(document_id,tenant_id,source_type,title,status,version,storage_key,metadata_json,created_by,updated_by,source_name,source_version,license_notice,visibility,current_version,revision) VALUES (?,0,'m16',?,'indexed','1',?,'{}'::jsonb,?,?,?,'1','m16-license','published',TRUE,1)",
                documentId,
                documentTitle,
                STORAGE_KEY,
                adminId,
                adminId,
                "m16-source-" + suffix);
        long chunkId = ids.nextId();
        chunkIds.add(chunkId);
        jdbc.update(
                "INSERT INTO knowledge_chunks(chunk_id,document_id,chunk_no,chunk_text,version,created_by,updated_by) VALUES (?,?,1,'m16 chunk','1',?,?)",
                chunkId,
                documentId,
                adminId,
                adminId);

        long deletedDocumentId = ids.nextId();
        documentIds.add(deletedDocumentId);
        jdbc.update(
                "INSERT INTO knowledge_documents(document_id,tenant_id,source_type,title,status,version,storage_key,metadata_json,created_by,updated_by,visibility,current_version,revision,is_deleted,deleted_at) VALUES (?,0,'m16',?,'disabled','1',?,'{}'::jsonb,?,?, 'deleted',TRUE,1,TRUE,CURRENT_TIMESTAMP)",
                deletedDocumentId,
                "m16-deleted-" + suffix,
                "m16-deleted-storage-" + suffix,
                adminId,
                adminId);

        long eventId = ids.nextId();
        eventIds.add(eventId);
        jdbc.update(
                "INSERT INTO runtime_event_inbox_v2(runtime_event_inbox_id,agent_run_id,dispatch_id,attempt,event_id,event_seq,event_type,occurred_at,payload_json,request_hash,processing_status,applied_at) VALUES (?,?,?,1,?,1,'run.accepted',CURRENT_TIMESTAMP,?::jsonb,?,'applied',CURRENT_TIMESTAMP)",
                eventId,
                runId,
                "m16-dispatch-" + suffix,
                "m16-event-" + suffix,
                "{\"raw_payload\":\"" + RAW_PAYLOAD + "\"}",
                "sha256:m16-" + suffix);

        long sseId = ids.nextId();
        sseOutboxIds.add(sseId);
        jdbc.update(
                "INSERT INTO agent_run_sse_outbox(agent_run_sse_outbox_id,agent_run_id,sse_event_id,stream_seq,source_event_key,event_type,payload_json,status,sent_at) VALUES (?,?,?,1,?,'run.completed',?::jsonb,'sent',CURRENT_TIMESTAMP)",
                sseId,
                runId,
                "m16-sse-" + suffix,
                "m16-source-event-" + suffix,
                "{\"raw_payload\":\"" + RAW_PAYLOAD + "\"}");

        String action = "m16.read.audit." + suffix;
        long operationAuditId = ids.nextId();
        operationAuditIds.add(operationAuditId);
        jdbc.update(
                "INSERT INTO operation_audits(operation_audit_id,operator_id,request_id,trace_id,target_type,target_id,action,result,request_json,response_json,created_by,updated_by) VALUES (?,?,?,?,?,?,?,'success',?::jsonb,?::jsonb,?,?)",
                operationAuditId,
                adminId,
                "m16-request-audit-" + suffix,
                traceId,
                "run",
                Long.toString(runId),
                action,
                "{\"raw_payload\":\"" + RAW_PAYLOAD + "\"}",
                "{\"storage_key\":\"" + STORAGE_KEY + "\"}",
                adminId,
                adminId);

        long dlqId = ids.nextId();
        dlqIds.add(dlqId);
        jdbc.update(
                "INSERT INTO runtime_message_dlq(dlq_id,consumer_group,source_topic,mq_message_id,run_id,dispatch_id,attempt,event_id,event_seq,request_hash,reconsume_times,error_code,last_error,raw_payload_json,reconciliation_state,reconciled_at) VALUES (?,?,?,?,?,?,?,?,?,?,2,'M16_TEST_FAILURE','m16 failure',?::jsonb,'needs_attention',CURRENT_TIMESTAMP)",
                dlqId,
                "m16-consumer-" + suffix,
                "m16-topic",
                "m16-message-" + suffix,
                Long.toString(runId),
                "m16-dlq-dispatch-" + suffix,
                3,
                "m16-dlq-event-" + suffix,
                1L,
                "sha256:m16-dlq-" + suffix,
                "{\"raw_payload\":\"" + RAW_PAYLOAD + "\"}");

        return new Facts(
                "m16_user_" + suffix,
                traceId,
                toolName,
                "m16-provider-" + suffix,
                documentTitle,
                deletedDocumentId,
                action,
                "m16-consumer-" + suffix);
    }

    private void assertPageContains(String path, HttpHeaders headers, String resource, String value)
            throws Exception {
        JsonNode page = page(path, headers, resource);
        assertTrue(page.path("items").size() > 0, path + " returned no items");
        assertTrue(page.toString().contains(value), path + " did not return the filtered fact");
        assertEquals(1, page.path("page").asInt());
        assertEquals(1, page.path("size").asInt());
    }

    private JsonNode page(String path, HttpHeaders headers, String resource) throws Exception {
        ResponseEntity<String> response = get(path, headers);
        assertEquals(200, response.getStatusCode().value(), path + " " + response.getBody());
        JsonNode value = data(response);
        assertEquals(resource, value.path("resource").asText());
        assertTrue(value.path("items").isArray());
        assertTrue(value.path("total").asLong() >= value.path("items").size());
        return value;
    }

    private Set<String> adminReadPaths(Facts facts) {
        Set<String> paths = new HashSet<>();
        paths.add("/api/admin/dashboard");
        for (String resource :
                Set.of(
                        "runs",
                        "traces",
                        "users",
                        "tool-calls",
                        "sql-audits",
                        "tools",
                        "usage",
                        "knowledge",
                        "deleted",
                        "operation-audits",
                        "dlq")) {
            paths.add("/api/admin/queries/" + resource + "?page=1&size=1");
        }
        paths.add("/api/admin/queries/traces/" + facts.traceId());
        paths.add("/api/admin/audit-reports/current");
        return paths;
    }

    private boolean containsLong(JsonNode values, String field, long expected) {
        for (JsonNode value : values) {
            if (expected == value.path(field).asLong(Long.MIN_VALUE)) return true;
        }
        return false;
    }

    private long register(String username) throws Exception {
        ResponseEntity<String> response =
                rest.postForEntity(
                        url("/api/auth/register"),
                        new HttpEntity<>(
                                "{\"username\":\""
                                        + username
                                        + "\",\"email\":\""
                                        + username
                                        + "@example.com\",\"password\":\"password123\"}",
                                jsonHeaders()),
                        String.class);
        assertEquals(200, response.getStatusCode().value(), response.getBody());
        long userId = json.readTree(response.getBody()).path("data").path("user_id").asLong();
        assertTrue(userId > 0);
        return userId;
    }

    private HttpHeaders login(String username) throws Exception {
        ResponseEntity<String> response =
                rest.postForEntity(
                        url("/api/auth/login"),
                        new HttpEntity<>(
                                "{\"username_or_email\":\""
                                        + username
                                        + "\",\"password\":\"password123\"}",
                                jsonHeaders()),
                        String.class);
        assertEquals(200, response.getStatusCode().value(), response.getBody());
        String session = cookie(response, "foodmate_session");
        String csrf = cookie(response, "foodmate_csrf");
        HttpHeaders headers = jsonHeaders();
        headers.set(HttpHeaders.COOKIE, "foodmate_session=" + session + "; foodmate_csrf=" + csrf);
        headers.set("X-CSRF-Token", csrf);
        return headers;
    }

    private ResponseEntity<String> get(String path, HttpHeaders sourceHeaders) {
        HttpHeaders headers = new HttpHeaders();
        headers.putAll(sourceHeaders);
        return rest.exchange(url(path), HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private JsonNode data(ResponseEntity<String> response) throws Exception {
        assertNotNull(response.getBody());
        return json.readTree(response.getBody()).path("data");
    }

    private String body(ResponseEntity<String> response) {
        assertNotNull(response.getBody());
        return response.getBody();
    }

    private String cookie(ResponseEntity<String> response, String name) {
        return response.getHeaders().get(HttpHeaders.SET_COOKIE).stream()
                .flatMap(value -> Arrays.stream(value.split(";")))
                .map(String::trim)
                .filter(value -> value.startsWith(name + "="))
                .map(value -> value.substring(name.length() + 1))
                .findFirst()
                .orElseThrow();
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private boolean tableExists(String tableName) {
        Boolean exists =
                jdbc.queryForObject(
                        "SELECT to_regclass(?) IS NOT NULL", Boolean.class, "public." + tableName);
        return Boolean.TRUE.equals(exists);
    }

    private record Facts(
            String username,
            String traceId,
            String toolName,
            String providerCode,
            String documentTitle,
            long deletedDocumentId,
            String action,
            String consumerGroup) {}
}
