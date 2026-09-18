package com.foodmate.bootstrap.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.foodmate.application.account.port.out.PasswordResetNotifier;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

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
class M11AdminManagementE2ETest {
    @LocalServerPort int port;
    @Autowired TestRestTemplate rest;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired MinioClient minio;
    @Autowired Environment environment;
    @MockitoBean PasswordResetNotifier passwordResetNotifier;

    @BeforeEach
    void enablePasswordResetNotificationForE2e() {
        org.mockito.Mockito.when(passwordResetNotifier.isAvailable()).thenReturn(true);
    }

    @Test
    void adminWriteAndUserForbidden() throws Exception {
        assertEquals("foodmate-local", environment.getProperty("foodmate.storage.access-key"));
        String admin = "admin_" + UUID.randomUUID().toString().replace("-", "");
        String target = "target_" + UUID.randomUUID().toString().replace("-", "");
        String ordinary = "ordinary_" + UUID.randomUUID().toString().replace("-", "");
        long adminId = register(admin);
        long targetId = register(target);
        register(ordinary);
        jdbc.update("UPDATE users SET role='admin' WHERE user_id=?", adminId);

        ResponseEntity<String> adminLogin = login(admin);
        String adminCookie = cookie(adminLogin, "foodmate_session");
        String csrf = cookie(adminLogin, "foodmate_csrf");
        HttpHeaders adminHeaders = headers(adminCookie, csrf);
        ResponseEntity<String> write =
                rest.exchange(
                        url("/api/admin/users/" + targetId + "/status"),
                        HttpMethod.PATCH,
                        new HttpEntity<>(
                                userStatusRequest(targetId, "disabled"),
                                withIdempotencyKey(adminHeaders, "e2e-user-status-1")),
                        String.class);
        assertEquals(200, write.getStatusCode().value(), write.getBody());
        assertEquals(
                "disabled",
                jdbc.queryForObject(
                        "SELECT status FROM users WHERE user_id=?", String.class, targetId));
        assertTrue(
                jdbc.queryForObject(
                                "SELECT COUNT(*) FROM operation_audits WHERE operator_id=? AND action='admin.user.status.update'",
                                Long.class,
                                adminId)
                        > 0);

        long toolId =
                jdbc.queryForObject(
                        "SELECT COALESCE(MAX(tool_id),0)+1 FROM tool_registries", Long.class);
        jdbc.update(
                "INSERT INTO tool_registries(tool_id,name,status,created_by,updated_by) VALUES (?,?, 'active',?,?)",
                toolId,
                "e2e_tool_" + toolId,
                adminId,
                adminId);
        long toolRevision = toolRevision(toolId);
        ResponseEntity<String> tool =
                rest.exchange(
                        url("/api/admin/tools/e2e_tool_" + toolId + "/status"),
                        HttpMethod.PATCH,
                        new HttpEntity<>(
                                toolStatusRequest("e2e_tool_" + toolId, "disabled", toolRevision),
                                withIdempotencyKey(adminHeaders, "e2e-tool-status-1")),
                        String.class);
        assertEquals(200, tool.getStatusCode().value(), tool.getBody());
        assertEquals(
                "disabled",
                jdbc.queryForObject(
                        "SELECT status FROM tool_registries WHERE tool_id=?",
                        String.class,
                        toolId));

        long documentId =
                jdbc.queryForObject(
                        "SELECT COALESCE(MAX(document_id),0)+1 FROM knowledge_documents",
                        Long.class);
        jdbc.update(
                "INSERT INTO knowledge_documents(document_id,title,status,created_by,updated_by) VALUES (?,?, 'uploaded',?,?)",
                documentId,
                "e2e-doc-" + documentId,
                adminId,
                adminId);
        ResponseEntity<String> document =
                rest.exchange(
                        url("/api/admin/knowledge/" + documentId + "/status"),
                        HttpMethod.PATCH,
                        new HttpEntity<>("{\"status\":\"indexed\"}", adminHeaders),
                        String.class);
        assertEquals(200, document.getStatusCode().value(), document.getBody());
        assertEquals(
                "indexed",
                jdbc.queryForObject(
                        "SELECT status FROM knowledge_documents WHERE document_id=?",
                        String.class,
                        documentId));

        org.springframework.util.LinkedMultiValueMap<String, Object> multipart =
                new org.springframework.util.LinkedMultiValueMap<>();
        org.springframework.core.io.ByteArrayResource resource =
                new org.springframework.core.io.ByteArrayResource(
                        "# e2e knowledge".getBytes(java.nio.charset.StandardCharsets.UTF_8)) {
                    @Override
                    public String getFilename() {
                        return "e2e.md";
                    }
                };
        multipart.add("file", resource);
        HttpHeaders multipartHeaders = headers(adminCookie, csrf);
        multipartHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);
        ResponseEntity<String> upload =
                rest.postForEntity(
                        url("/api/admin/knowledge"),
                        new HttpEntity<>(multipart, multipartHeaders),
                        String.class);
        assertEquals(200, upload.getStatusCode().value(), upload.getBody());
        long uploadedId = json.readTree(upload.getBody()).path("data").path("document_id").asLong();
        String storageKey =
                jdbc.queryForObject(
                        "SELECT storage_key FROM knowledge_documents WHERE document_id=?",
                        String.class,
                        uploadedId);
        assertTrue(
                minio.statObject(
                                        StatObjectArgs.builder()
                                                .bucket("foodmate-private")
                                                .object(storageKey)
                                                .build())
                                .size()
                        > 0);

        ResponseEntity<String> revoke =
                rest.postForEntity(
                        url("/api/admin/users/" + targetId + "/sessions/revoke-all"),
                        new HttpEntity<>(
                                confirmedMutationRequest(
                                        "admin.user.sessions.revoke_all",
                                        Long.toString(targetId),
                                        "",
                                        userRevision(targetId)),
                                withIdempotencyKey(adminHeaders, "e2e-revoke-sessions-1")),
                        String.class);
        assertEquals(200, revoke.getStatusCode().value(), revoke.getBody());

        long deletedId = register("restore_" + UUID.randomUUID().toString().replace("-", ""));
        jdbc.update(
                "UPDATE users SET is_deleted=TRUE, deleted_at=CURRENT_TIMESTAMP, status='disabled' WHERE user_id=?",
                deletedId);
        long deletedRevision = userRevision(deletedId);
        ResponseEntity<String> restore =
                rest.postForEntity(
                        url("/api/admin/resources/user/" + deletedId + "/restore"),
                        new HttpEntity<>(
                                confirmedMutationRequest(
                                        "admin.resource.restore",
                                        "user",
                                        Long.toString(deletedId),
                                        deletedRevision),
                                withIdempotencyKey(adminHeaders, "e2e-restore-user-1")),
                        String.class);
        assertEquals(200, restore.getStatusCode().value(), restore.getBody());
        assertEquals(
                false,
                jdbc.queryForObject(
                        "SELECT is_deleted FROM users WHERE user_id=?", Boolean.class, deletedId));

        ResponseEntity<String> userLogin = login(ordinary);
        HttpHeaders userHeaders =
                headers(cookie(userLogin, "foodmate_session"), cookie(userLogin, "foodmate_csrf"));
        ResponseEntity<String> forbidden =
                rest.exchange(
                        url("/api/admin/users/" + adminId + "/status"),
                        HttpMethod.PATCH,
                        new HttpEntity<>(
                                userStatusRequest(adminId, "active"),
                                withIdempotencyKey(userHeaders, "e2e-forbidden-user-status-1")),
                        String.class);
        assertEquals(403, forbidden.getStatusCode().value());
    }

    @Test
    void adminCredentialResetRevokesExistingUserSession() throws Exception {
        String admin = "credential_admin_" + UUID.randomUUID().toString().replace("-", "");
        String target = "credential_target_" + UUID.randomUUID().toString().replace("-", "");
        long adminId = register(admin);
        long targetId = register(target);
        jdbc.update("UPDATE users SET role='admin' WHERE user_id=?", adminId);

        ResponseEntity<String> adminLogin = login(admin);
        HttpHeaders adminHeaders =
                headers(
                        cookie(adminLogin, "foodmate_session"),
                        cookie(adminLogin, "foodmate_csrf"));
        ResponseEntity<String> targetLogin = login(target);
        HttpHeaders targetHeaders =
                headers(
                        cookie(targetLogin, "foodmate_session"),
                        cookie(targetLogin, "foodmate_csrf"));

        long revision = userRevision(targetId);
        ResponseEntity<String> reset =
                rest.postForEntity(
                        url("/api/admin/users/" + targetId + "/credentials/reset"),
                        new HttpEntity<>(
                                confirmedMutationRequest(
                                        "admin.user.credentials.reset",
                                        Long.toString(targetId),
                                        "",
                                        revision),
                                withIdempotencyKey(adminHeaders, "e2e-credential-reset-1")),
                        String.class);

        assertEquals(200, reset.getStatusCode().value(), reset.getBody());
        assertTrue(json.readTree(reset.getBody()).path("data").path("requested").asBoolean());
        assertTrue(json.readTree(reset.getBody()).path("data").path("token").isMissingNode());
        assertEquals(
                revision + 1,
                jdbc.queryForObject(
                        "SELECT revision FROM users WHERE user_id=?", Long.class, targetId));

        ResponseEntity<String> oldSession =
                rest.exchange(
                        url("/api/users/me"),
                        HttpMethod.GET,
                        new HttpEntity<>(targetHeaders),
                        String.class);
        assertEquals(401, oldSession.getStatusCode().value(), oldSession.getBody());
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM user_auth_sessions WHERE user_id=? AND revoked_at IS NULL",
                        Integer.class,
                        targetId));
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM auth_refresh_tokens WHERE user_id=? AND revoked_at IS NULL",
                        Integer.class,
                        targetId));
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
        return json.readTree(response.getBody()).path("data").path("user_id").asLong();
    }

    private ResponseEntity<String> login(String username) {
        return rest.postForEntity(
                url("/api/auth/login"),
                new HttpEntity<>(
                        "{\"username_or_email\":\"" + username + "\",\"password\":\"password123\"}",
                        jsonHeaders()),
                String.class);
    }

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    private HttpHeaders headers(String session, String csrf) {
        HttpHeaders h = jsonHeaders();
        h.set(HttpHeaders.COOKIE, "foodmate_session=" + session + "; foodmate_csrf=" + csrf);
        h.set("X-CSRF-Token", csrf);
        return h;
    }

    private HttpHeaders withIdempotencyKey(HttpHeaders headers, String value) {
        headers.set("Idempotency-Key", value);
        return headers;
    }

    private String userStatusRequest(long userId, String status) throws Exception {
        long revision = userRevision(userId);
        ObjectNode body = json.createObjectNode();
        body.put("status", status);
        putConfirmation(body, "admin.user.status.update", Long.toString(userId), status, revision);
        return body.toString();
    }

    private String toolStatusRequest(String name, String status, long revision) throws Exception {
        ObjectNode body = json.createObjectNode();
        body.put("status", status);
        putConfirmation(body, "admin.tool.status.update", name, status, revision);
        return body.toString();
    }

    private String confirmedMutationRequest(
            String action, String target, String value, long revision) throws Exception {
        ObjectNode body = json.createObjectNode();
        putConfirmation(body, action, target, value, revision);
        return body.toString();
    }

    private void putConfirmation(
            ObjectNode body, String action, String target, String value, long revision)
            throws Exception {
        body.put("revision", revision);
        body.put("confirmed", true);
        body.put("confirmationDigest", confirmationDigest(action, target, value, revision));
    }

    private String confirmationDigest(String action, String target, String value, long revision)
            throws Exception {
        byte[] input =
                (action + "|" + target + "|" + value + "|" + revision)
                        .getBytes(StandardCharsets.UTF_8);
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input));
    }

    private long userRevision(long userId) {
        return jdbc.queryForObject(
                "SELECT revision FROM users WHERE user_id=?", Long.class, userId);
    }

    private long toolRevision(long toolId) {
        return jdbc.queryForObject(
                "SELECT revision FROM tool_registries WHERE tool_id=?", Long.class, toolId);
    }

    private String cookie(ResponseEntity<String> response, String name) {
        return response.getHeaders().get(HttpHeaders.SET_COOKIE).stream()
                .flatMap(v -> java.util.Arrays.stream(v.split(";")))
                .map(String::trim)
                .filter(v -> v.startsWith(name + "="))
                .map(v -> v.substring(name.length() + 1))
                .findFirst()
                .orElseThrow();
    }
}
