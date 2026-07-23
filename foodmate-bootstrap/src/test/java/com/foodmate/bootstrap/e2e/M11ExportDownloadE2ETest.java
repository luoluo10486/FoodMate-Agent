package com.foodmate.bootstrap.e2e;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
@TestPropertySource(properties = {"foodmate.storage.endpoint=http://localhost:9000", "foodmate.storage.access-key=foodmate-local", "foodmate.storage.secret-key=foodmate-local-secret-change-me-20260722", "foodmate.storage.bucket=foodmate-private", "foodmate.account.jobs-delay-ms=1000"})
@EnabledIfSystemProperty(named = "foodmate.local-e2e", matches = "true")
class M11ExportDownloadE2ETest {
    @LocalServerPort int port;
    @Autowired TestRestTemplate rest;
    @Autowired ObjectMapper json;
    @Autowired com.foodmate.application.account.PersonalDataService personal;

    @Test
    void exportCompletesAndDownloadLinkIsOneTime() throws Exception {
        String base = "http://localhost:" + port;
        String username = "export_" + UUID.randomUUID().toString().replace("-", "");
        ResponseEntity<String> reg = rest.postForEntity(base + "/api/auth/register", new HttpEntity<>("{\"username\":\"" + username + "\",\"email\":\"" + username + "@example.com\",\"password\":\"password123\"}", headers()), String.class);
        assertEquals(200, reg.getStatusCode().value());
        long userId = json.readTree(reg.getBody()).path("data").path("user_id").asLong();
        ResponseEntity<String> login = rest.postForEntity(base + "/api/auth/login", new HttpEntity<>("{\"username_or_email\":\"" + username + "\",\"password\":\"password123\"}", headers()), String.class);
        assertEquals(200, login.getStatusCode().value());
        String joined = String.join("; ", login.getHeaders().get(HttpHeaders.SET_COOKIE));
        String sessionCookie = joined.split(";")[0];
        String csrfCookie = java.util.Arrays.stream(joined.split(";\\s*")).filter(v -> v.startsWith("foodmate_csrf=")).findFirst().orElseThrow();
        String csrf = csrfCookie.substring("foodmate_csrf=".length());
        long jobId = personal.requestExport(userId);
        for (int i = 0; i < 10; i++) { Thread.sleep(1000); }
        HttpHeaders auth = headers();
        auth.set(HttpHeaders.COOKIE, sessionCookie + "; " + csrfCookie);
        ResponseEntity<String> status = rest.exchange(URI.create(base + "/api/users/me/export/" + jobId), HttpMethod.GET, new HttpEntity<>(auth), String.class);
        assertEquals(200, status.getStatusCode().value(), status.getBody());
        assertEquals("completed", json.readTree(status.getBody()).path("data").path("status").asText());
        auth.set("X-CSRF-Token", csrf);
        ResponseEntity<String> download = rest.postForEntity(base + "/api/users/me/export/" + jobId + "/download", new HttpEntity<>("", auth), String.class);
        assertEquals(200, download.getStatusCode().value(), download.getBody());
        String url = json.readTree(download.getBody()).path("data").path("download_url").asText();
        assertTrue(url.startsWith("http"));
        ResponseEntity<String> archive = rest.exchange(URI.create(url), HttpMethod.GET, HttpEntity.EMPTY, String.class);
        assertEquals(200, archive.getStatusCode().value(), archive.getBody());
        assertNotNull(archive.getBody());
        ResponseEntity<String> second = rest.postForEntity(base + "/api/users/me/export/" + jobId + "/download", new HttpEntity<>("", auth), String.class);
        assertEquals(409, second.getStatusCode().value());
    }

    private HttpHeaders headers() { HttpHeaders h = new HttpHeaders(); h.setContentType(MediaType.APPLICATION_JSON); return h; }
}
