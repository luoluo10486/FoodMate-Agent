package com.foodmate.bootstrap.e2e;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.minio.MinioClient;
import io.minio.StatObjectArgs;
import io.minio.PutObjectArgs;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.util.LinkedMultiValueMap;

/** M1-1 real PostgreSQL + MinIO account deletion acceptance. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
@TestPropertySource(properties = {"foodmate.storage.endpoint=http://localhost:9000", "foodmate.storage.access-key=foodmate-local", "foodmate.storage.secret-key=foodmate-local-secret-change-me-20260722", "foodmate.storage.bucket=foodmate-private"})
@EnabledIfSystemProperty(named = "foodmate.local-e2e", matches = "true")
class M11AccountDeletionE2ETest {
    @LocalServerPort int port;
    @Autowired TestRestTemplate rest;
    @Autowired ObjectMapper json;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired MinioClient minio;
    @Autowired com.foodmate.application.account.PersonalDataService personal;

    @Test
    void accountDeletionRevokesAndCleansPostgresAndMinio() throws Exception {
        String username = "delete_" + UUID.randomUUID().toString().replace("-", "");
        String base = "http://localhost:" + port;
        ResponseEntity<String> reg = rest.postForEntity(base + "/api/auth/register", new HttpEntity<>("{\"username\":\"" + username + "\",\"email\":\"" + username + "@example.com\",\"password\":\"password123\"}", jsonHeaders()), String.class);
        assertEquals(200, reg.getStatusCode().value());
        ResponseEntity<String> login = rest.postForEntity(base + "/api/auth/login", new HttpEntity<>("{\"username_or_email\":\"" + username + "\",\"password\":\"password123\"}", jsonHeaders()), String.class);
        assertEquals(200, login.getStatusCode().value());
        String setCookie = String.join("; ", login.getHeaders().get(HttpHeaders.SET_COOKIE));
        String session = java.util.regex.Pattern.compile("foodmate_session=([^;,\\s]+)").matcher(setCookie).results().findFirst().map(m -> "foodmate_session=" + m.group(1)).orElseThrow();
        String csrf = java.util.regex.Pattern.compile("foodmate_csrf=([^;,\\s]+)").matcher(setCookie).results().findFirst().map(java.util.regex.MatchResult::group).orElseThrow().substring(13);
        HttpHeaders auth = jsonHeaders(); auth.set(HttpHeaders.COOKIE, session + "; foodmate_csrf=" + csrf); auth.set("X-CSRF-Token", csrf);
        long userId = json.readTree(reg.getBody()).path("data").path("user_id").asLong();
        String key = "avatars/" + userId + "/m11-delete-e2e.txt";
        minio.putObject(PutObjectArgs.builder().bucket("foodmate-private").object(key).stream(new ByteArrayInputStream("fake-png".getBytes()), 8, -1).contentType("text/plain").build());
        long avatarId = jdbc.queryForObject("SELECT COALESCE(MAX(avatar_asset_id),0)+1 FROM user_avatar_assets", Long.class);
        jdbc.update("INSERT INTO user_avatar_assets(avatar_asset_id,user_id,storage_key,url,mime_type,size_bytes,status,created_by) VALUES (?,?,?,NULL,'text/plain',8,'active',?)", avatarId, userId, key, userId);
        minio.statObject(StatObjectArgs.builder().bucket("foodmate-private").object(key).build());
        personal.requestDeletion(userId);
        for (int i = 0; i < 45; i++) { if ("completed".equals(jdbc.queryForObject("SELECT status FROM account_deletion_jobs WHERE user_id=? ORDER BY created_at DESC LIMIT 1", String.class, userId))) break; Thread.sleep(1000); }
        assertEquals("completed", jdbc.queryForObject("SELECT status FROM account_deletion_jobs WHERE user_id=? ORDER BY created_at DESC LIMIT 1", String.class, userId));
        assertEquals(Boolean.TRUE, jdbc.queryForObject("SELECT is_deleted FROM users WHERE user_id=?", Boolean.class, userId));
        assertThrows(Exception.class, () -> minio.statObject(StatObjectArgs.builder().bucket("foodmate-private").object(key).build()));
    }

    private HttpHeaders jsonHeaders() { HttpHeaders h = new HttpHeaders(); h.setContentType(MediaType.APPLICATION_JSON); return h; }
}
