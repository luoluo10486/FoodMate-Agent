package com.foodmate.bootstrap.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.core.env.Environment;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(properties = {"foodmate.storage.endpoint=http://localhost:9000", "foodmate.storage.access-key=foodmate-local", "foodmate.storage.secret-key=foodmate-local-secret-change-me-20260722", "foodmate.storage.bucket=foodmate-private"})
@EnabledIfSystemProperty(named = "foodmate.local-e2e", matches = "true")
class M11AdminManagementE2ETest {
    @LocalServerPort int port;
    @Autowired TestRestTemplate rest;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired MinioClient minio;
    @Autowired Environment environment;

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
        ResponseEntity<String> write = rest.exchange(url("/api/admin/users/" + targetId + "/status"), HttpMethod.PATCH,
                new HttpEntity<>("{\"status\":\"disabled\"}", adminHeaders), String.class);
        assertEquals(200, write.getStatusCode().value(), write.getBody());
        assertEquals("disabled", jdbc.queryForObject("SELECT status FROM users WHERE user_id=?", String.class, targetId));
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM operation_audits WHERE operator_id=? AND action='user.status.update'", Long.class, adminId) > 0);

        long toolId = jdbc.queryForObject("SELECT COALESCE(MAX(tool_id),0)+1 FROM tool_registries", Long.class);
        jdbc.update("INSERT INTO tool_registries(tool_id,name,status,created_by,updated_by) VALUES (?,?, 'active',?,?)", toolId, "e2e_tool_" + toolId, adminId, adminId);
        ResponseEntity<String> tool = rest.exchange(url("/api/admin/tools/e2e_tool_" + toolId + "/status"), HttpMethod.PATCH,
                new HttpEntity<>("{\"status\":\"disabled\"}", adminHeaders), String.class);
        assertEquals(200, tool.getStatusCode().value(), tool.getBody());
        assertEquals("disabled", jdbc.queryForObject("SELECT status FROM tool_registries WHERE tool_id=?", String.class, toolId));

        long documentId = jdbc.queryForObject("SELECT COALESCE(MAX(document_id),0)+1 FROM knowledge_documents", Long.class);
        jdbc.update("INSERT INTO knowledge_documents(document_id,title,status,created_by,updated_by) VALUES (?,?, 'uploaded',?,?)", documentId, "e2e-doc-" + documentId, adminId, adminId);
        ResponseEntity<String> document = rest.exchange(url("/api/admin/knowledge/" + documentId + "/status"), HttpMethod.PATCH,
                new HttpEntity<>("{\"status\":\"indexed\"}", adminHeaders), String.class);
        assertEquals(200, document.getStatusCode().value(), document.getBody());
        assertEquals("indexed", jdbc.queryForObject("SELECT status FROM knowledge_documents WHERE document_id=?", String.class, documentId));

        org.springframework.util.LinkedMultiValueMap<String, Object> multipart = new org.springframework.util.LinkedMultiValueMap<>();
        org.springframework.core.io.ByteArrayResource resource = new org.springframework.core.io.ByteArrayResource("# e2e knowledge".getBytes(java.nio.charset.StandardCharsets.UTF_8)) { @Override public String getFilename() { return "e2e.md"; } };
        multipart.add("file", resource);
        HttpHeaders multipartHeaders = headers(adminCookie, csrf); multipartHeaders.setContentType(MediaType.MULTIPART_FORM_DATA);
        ResponseEntity<String> upload = rest.postForEntity(url("/api/admin/knowledge"), new HttpEntity<>(multipart, multipartHeaders), String.class);
        assertEquals(200, upload.getStatusCode().value(), upload.getBody());
        long uploadedId = json.readTree(upload.getBody()).path("data").path("documentId").asLong();
        String storageKey = jdbc.queryForObject("SELECT storage_key FROM knowledge_documents WHERE document_id=?", String.class, uploadedId);
        assertTrue(minio.statObject(StatObjectArgs.builder().bucket("foodmate-private").object(storageKey).build()).size() > 0);


        ResponseEntity<String> revoke = rest.postForEntity(url("/api/admin/users/" + targetId + "/sessions/revoke-all"), new HttpEntity<>("", adminHeaders), String.class);
        assertEquals(200, revoke.getStatusCode().value(), revoke.getBody());

        long deletedId = register("restore_" + UUID.randomUUID().toString().replace("-", ""));
        jdbc.update("UPDATE users SET is_deleted=TRUE, deleted_at=CURRENT_TIMESTAMP, status='disabled' WHERE user_id=?", deletedId);
        ResponseEntity<String> restore = rest.postForEntity(url("/api/admin/resources/user/" + deletedId + "/restore"), new HttpEntity<>("", adminHeaders), String.class);
        assertEquals(200, restore.getStatusCode().value(), restore.getBody());
        assertEquals(false, jdbc.queryForObject("SELECT is_deleted FROM users WHERE user_id=?", Boolean.class, deletedId));

        ResponseEntity<String> userLogin = login(ordinary);
        HttpHeaders userHeaders = headers(cookie(userLogin, "foodmate_session"), cookie(userLogin, "foodmate_csrf"));
        ResponseEntity<String> forbidden = rest.exchange(url("/api/admin/users/" + adminId + "/status"), HttpMethod.PATCH,
                new HttpEntity<>("{\"status\":\"active\"}", userHeaders), String.class);
        assertEquals(403, forbidden.getStatusCode().value());
    }

    private long register(String username) throws Exception {
        ResponseEntity<String> response = rest.postForEntity(url("/api/auth/register"), new HttpEntity<>("{\"username\":\"" + username + "\",\"email\":\"" + username + "@example.com\",\"password\":\"password123\"}", jsonHeaders()), String.class);
        assertEquals(200, response.getStatusCode().value(), response.getBody());
        return json.readTree(response.getBody()).path("data").path("user_id").asLong();
    }
    private ResponseEntity<String> login(String username) { return rest.postForEntity(url("/api/auth/login"), new HttpEntity<>("{\"username_or_email\":\"" + username + "\",\"password\":\"password123\"}", jsonHeaders()), String.class); }
    private String url(String path) { return "http://localhost:" + port + path; }
    private HttpHeaders jsonHeaders() { HttpHeaders h = new HttpHeaders(); h.setContentType(MediaType.APPLICATION_JSON); return h; }
    private HttpHeaders headers(String session, String csrf) { HttpHeaders h = jsonHeaders(); h.set(HttpHeaders.COOKIE, "foodmate_session=" + session + "; foodmate_csrf=" + csrf); h.set("X-CSRF-Token", csrf); return h; }
    private String cookie(ResponseEntity<String> response, String name) { return response.getHeaders().get(HttpHeaders.SET_COOKIE).stream().flatMap(v -> java.util.Arrays.stream(v.split(";"))).map(String::trim).filter(v -> v.startsWith(name + "=")).map(v -> v.substring(name.length() + 1)).findFirst().orElseThrow(); }
}
