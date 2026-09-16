package com.foodmate.bootstrap.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodmate.shared.id.IdGenerator;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HexFormat;
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

/** M11 数据保留治理真实 HTTP 闭环；只使用随机临时账号和知识文档。 */
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
class M11RetentionGovernanceE2ETest {
    @LocalServerPort int port;
    @Autowired TestRestTemplate rest;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired IdGenerator ids;

    private final Set<Long> userIds = new HashSet<>();
    private final Set<Long> documentIds = new HashSet<>();
    private final Set<Long> requestIds = new HashSet<>();
    private final Set<Long> holdIds = new HashSet<>();

    @AfterEach
    void cleanTemporaryFacts() {
        for (long holdId : holdIds) {
            jdbc.update("DELETE FROM data_legal_holds WHERE hold_id=?", holdId);
        }
        for (long requestId : requestIds) {
            if (tableExists("data_purge_task_results")) {
                jdbc.update("DELETE FROM data_purge_task_results WHERE request_id=?", requestId);
            }
            jdbc.update("DELETE FROM data_purge_tasks WHERE request_id=?", requestId);
            jdbc.update("DELETE FROM data_purge_requests WHERE request_id=?", requestId);
        }
        for (long documentId : documentIds) {
            jdbc.update("DELETE FROM knowledge_visibility_outbox WHERE document_id=?", documentId);
            jdbc.update("DELETE FROM knowledge_import_items WHERE document_id=?", documentId);
            jdbc.update("DELETE FROM knowledge_chunks WHERE document_id=?", documentId);
            jdbc.update("DELETE FROM knowledge_documents WHERE document_id=?", documentId);
        }
        for (long userId : userIds) {
            jdbc.update("DELETE FROM operation_audits WHERE operator_id=?", userId);
            jdbc.update(
                    "UPDATE user_auth_sessions SET revoked_at=CURRENT_TIMESTAMP WHERE user_id=?",
                    userId);
            jdbc.update(
                    "UPDATE auth_refresh_tokens SET revoked_at=CURRENT_TIMESTAMP WHERE user_id=?",
                    userId);
            jdbc.update(
                    "UPDATE user_profiles SET is_deleted=TRUE,deleted_at=CURRENT_TIMESTAMP WHERE user_id=?",
                    userId);
            jdbc.update(
                    "UPDATE users SET is_deleted=TRUE,status='disabled',deleted_at=CURRENT_TIMESTAMP WHERE user_id=?",
                    userId);
        }
    }

    @Test
    void retentionLifecycleHonorsHoldApprovalAndHardDeletePolicy() throws Exception {
        String adminName = "retention_admin_" + suffix();
        String superadminName = "retention_superadmin_" + suffix();
        String operatorName = "retention_operator_" + suffix();
        long adminId = register(adminName);
        long superadminId = register(superadminName);
        long operatorId = register(operatorName);
        userIds.addAll(Set.of(adminId, superadminId, operatorId));
        jdbc.update("UPDATE users SET role='admin' WHERE user_id=?", adminId);
        jdbc.update("UPDATE users SET role='superadmin' WHERE user_id=?", superadminId);
        jdbc.update("UPDATE users SET role='operator' WHERE user_id=?", operatorId);

        HttpHeaders admin = login(adminName);
        HttpHeaders superadmin = login(superadminName);
        HttpHeaders operator = login(operatorName);
        long documentId = insertExpiredSoftDeletedDocument(adminId);

        ResponseEntity<String> create =
                post(
                        "/api/admin/data-retention/purge-requests",
                        admin,
                        "retention-create-" + documentId,
                        "{\"resource_type\":\"knowledge_document\",\"resource_id\":"
                                + documentId
                                + ",\"confirmed\":true,\"confirmation_digest\":\""
                                + sha256("retention.purge|knowledge_document|" + documentId + "|1")
                                + "\"}");
        assertEquals(200, create.getStatusCode().value(), create.getBody());
        JsonNode created = data(create);
        long requestId = created.path("request_id").asLong();
        requestIds.add(requestId);
        assertTrue(requestId > 0);
        assertEquals("requested", created.path("status").asText());

        ResponseEntity<String> replay =
                post(
                        "/api/admin/data-retention/purge-requests",
                        admin,
                        "retention-create-" + documentId,
                        "{\"resource_type\":\"knowledge_document\",\"resource_id\":"
                                + documentId
                                + ",\"confirmed\":true,\"confirmation_digest\":\""
                                + sha256("retention.purge|knowledge_document|" + documentId + "|1")
                                + "\"}");
        assertEquals(200, replay.getStatusCode().value(), replay.getBody());
        assertEquals(requestId, data(replay).path("request_id").asLong());

        ResponseEntity<String> detail =
                get("/api/admin/data-retention/purge-requests/" + requestId, operator);
        assertEquals(200, detail.getStatusCode().value(), detail.getBody());
        assertEquals(requestId, data(detail).path("request_id").asLong());
        assertEquals("knowledge_document", data(detail).path("resource_type").asText());

        ResponseEntity<String> initialPreflight =
                get(
                        "/api/admin/data-retention/purge-requests/" + requestId + "/preflight",
                        operator);
        assertEquals(200, initialPreflight.getStatusCode().value(), initialPreflight.getBody());
        JsonNode initialChecks = data(initialPreflight);
        assertTrue(initialChecks.path("resource_soft_deleted").asBoolean());
        assertTrue(initialChecks.path("retention_elapsed").asBoolean());
        assertTrue(initialChecks.path("legal_hold_clear").asBoolean());
        assertFalse(initialChecks.path("ready_to_execute").asBoolean());
        assertTrue(hasBlocker(initialChecks, "RETENTION_REQUEST_NOT_APPROVED"));
        assertTrue(hasBlocker(initialChecks, "RETENTION_HARD_DELETE_DISABLED"));

        ResponseEntity<String> hold =
                post(
                        "/api/admin/data-retention/holds",
                        admin,
                        "retention-hold-" + documentId,
                        "{\"resource_type\":\"knowledge_document\",\"resource_id\":"
                                + documentId
                                + ",\"reason_code\":\"legal_request\",\"confirmed\":true,\"confirmation_digest\":\""
                                + sha256(
                                        "retention.hold|knowledge_document|"
                                                + documentId
                                                + "|legal_request|1")
                                + "\"}");
        assertEquals(200, hold.getStatusCode().value(), hold.getBody());
        long holdId = data(hold).path("hold_id").asLong();
        holdIds.add(holdId);
        assertEquals("active", data(hold).path("status").asText());

        ResponseEntity<String> heldPreflight =
                get(
                        "/api/admin/data-retention/purge-requests/" + requestId + "/preflight",
                        operator);
        assertEquals(200, heldPreflight.getStatusCode().value(), heldPreflight.getBody());
        JsonNode heldChecks = data(heldPreflight);
        assertFalse(heldChecks.path("legal_hold_clear").asBoolean());
        assertTrue(hasBlocker(heldChecks, "RETENTION_HOLD_ACTIVE"));
        assertFalse(heldChecks.path("ready_to_execute").asBoolean());

        ResponseEntity<String> operatorApproval =
                post(
                        "/api/admin/data-retention/purge-requests/" + requestId + "/approve",
                        operator,
                        "retention-operator-approve-" + requestId,
                        approvalBody(requestId));
        assertEquals(403, operatorApproval.getStatusCode().value(), operatorApproval.getBody());
        assertEquals("FORBIDDEN", errorCode(operatorApproval));

        ResponseEntity<String> heldApproval =
                post(
                        "/api/admin/data-retention/purge-requests/" + requestId + "/approve",
                        superadmin,
                        "retention-held-approve-" + requestId,
                        approvalBody(requestId));
        assertEquals(409, heldApproval.getStatusCode().value(), heldApproval.getBody());
        assertEquals("RETENTION_HOLD_ACTIVE", errorCode(heldApproval));

        ResponseEntity<String> release =
                post(
                        "/api/admin/data-retention/holds/" + holdId + "/release",
                        superadmin,
                        "retention-release-" + holdId,
                        "{\"confirmed\":true,\"confirmation_digest\":\""
                                + sha256("retention.release|" + holdId + "|1")
                                + "\"}");
        assertEquals(200, release.getStatusCode().value(), release.getBody());
        assertEquals("released", data(release).path("status").asText());

        ResponseEntity<String> approval =
                post(
                        "/api/admin/data-retention/purge-requests/" + requestId + "/approve",
                        superadmin,
                        "retention-approve-" + requestId,
                        approvalBody(requestId));
        assertEquals(200, approval.getStatusCode().value(), approval.getBody());
        JsonNode approved = data(approval);
        assertEquals("approved", approved.path("status").asText());
        assertEquals(3, approved.path("task_count").asInt());
        assertEquals(
                3,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM data_purge_tasks WHERE request_id=?",
                        Integer.class,
                        requestId));

        ResponseEntity<String> approvedPreflight =
                get(
                        "/api/admin/data-retention/purge-requests/" + requestId + "/preflight",
                        operator);
        assertEquals(200, approvedPreflight.getStatusCode().value(), approvedPreflight.getBody());
        JsonNode approvedChecks = data(approvedPreflight);
        assertTrue(approvedChecks.path("resource_soft_deleted").asBoolean());
        assertTrue(approvedChecks.path("retention_elapsed").asBoolean());
        assertTrue(approvedChecks.path("legal_hold_clear").asBoolean());
        assertTrue(approvedChecks.path("task_contract_valid").asBoolean());
        assertFalse(approvedChecks.path("hard_delete_enabled").asBoolean());
        assertFalse(approvedChecks.path("ready_to_execute").asBoolean());
        assertTrue(hasBlocker(approvedChecks, "RETENTION_HARD_DELETE_DISABLED"));
        assertFalse(approvedChecks.toString().contains("e2e-retention/"));
        assertTrue(
                jdbc.queryForObject(
                                "SELECT COUNT(*) FROM operation_audits WHERE operator_id IN (?,?) AND action LIKE 'retention.%'",
                                Integer.class, adminId, superadminId)
                        >= 3);
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

    private HttpHeaders login(String username) {
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

    private long insertExpiredSoftDeletedDocument(long ownerId) {
        long documentId = ids.nextId();
        documentIds.add(documentId);
        jdbc.update(
                "INSERT INTO knowledge_documents(document_id,tenant_id,source_type,title,status,version,storage_key,metadata_json,created_by,updated_by,is_deleted,deleted_at,visibility,current_version,revision) VALUES (?,0,'e2e',?,'disabled','1',?,'{}'::jsonb,?,?,TRUE,CURRENT_TIMESTAMP-INTERVAL '400 days','deleted',TRUE,1)",
                documentId,
                "e2e-retention-" + documentId,
                "e2e-retention/" + documentId + ".md",
                ownerId,
                ownerId);
        return documentId;
    }

    private ResponseEntity<String> get(String path, HttpHeaders headers) {
        return rest.exchange(url(path), HttpMethod.GET, new HttpEntity<>(headers), String.class);
    }

    private ResponseEntity<String> post(
            String path, HttpHeaders sourceHeaders, String idempotencyKey, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.putAll(sourceHeaders);
        headers.set("Idempotency-Key", idempotencyKey);
        return rest.exchange(
                url(path), HttpMethod.POST, new HttpEntity<>(body, headers), String.class);
    }

    private JsonNode data(ResponseEntity<String> response) throws Exception {
        assertNotNull(response.getBody());
        return json.readTree(response.getBody()).path("data");
    }

    private String errorCode(ResponseEntity<String> response) throws Exception {
        assertNotNull(response.getBody());
        return json.readTree(response.getBody()).path("error").path("code").asText();
    }

    private boolean hasBlocker(JsonNode preflight, String expected) {
        for (JsonNode blocker : preflight.path("blockers")) {
            if (expected.equals(blocker.asText())) return true;
        }
        return false;
    }

    private boolean tableExists(String tableName) {
        return Boolean.TRUE.equals(
                jdbc.queryForObject(
                        "SELECT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema='public' AND table_name=?)",
                        Boolean.class,
                        tableName));
    }

    private String approvalBody(long requestId) {
        return "{\"confirmed\":true,\"confirmation_digest\":\""
                + sha256("retention.approve|" + requestId + "|1")
                + "\"}";
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
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

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private String suffix() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private String sha256(String value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
