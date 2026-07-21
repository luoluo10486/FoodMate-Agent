package com.foodmate.bootstrap.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

/** Explicit local PostgreSQL acceptance test. Run with -Dfoodmate.local-e2e=true. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
@EnabledIfSystemProperty(named = "foodmate.local-e2e", matches = "true")
class LocalPostgresE2ETest {
    @LocalServerPort int port;
    private final TestRestTemplate rest = new TestRestTemplate();
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void registrationLoginConversationAndMessageUsePostgres() throws Exception {
        String base = "http://localhost:" + port;
        String username = "e2e_" + UUID.randomUUID().toString().replace("-", "");
        JsonNode register = post(base + "/api/auth/register", "{\"username\":\"" + username + "\",\"email\":\"" + username + "@example.com\",\"password\":\"password123\"}", null);
        assertNotNull(register.path("data").path("user_id").asText(null));

        ResponseEntity<String> loginResponse = rest.postForEntity(base + "/api/auth/login",
                new HttpEntity<>("{\"username_or_email\":\"" + username + "\",\"password\":\"password123\"}", jsonHeaders()), String.class);
        assertEquals(200, loginResponse.getStatusCode().value());
        JsonNode login = json.readTree(loginResponse.getBody());
        String cookies = String.join("; ", loginResponse.getHeaders().get(HttpHeaders.SET_COOKIE));
        String csrf = cookies.lines().flatMap(line -> List.of(line.split(";\\s*")).stream()).filter(value -> value.startsWith("foodmate_csrf=")).map(value -> value.substring("foodmate_csrf=".length())).findFirst().orElseThrow();
        HttpHeaders headers = jsonHeaders();
        headers.set(HttpHeaders.COOKIE, cookies);
        headers.set("X-CSRF-Token", csrf);
        JsonNode session = post(base + "/api/sessions", "{\"title\":\"local e2e\",\"mode\":\"chat\"}", headers);
        String sessionId = session.path("data").path("session_id").asText();
        JsonNode message = post(base + "/api/sessions/" + sessionId + "/messages", "{\"role\":\"user\",\"content\":\"postgres-persisted\"}", headers);
        assertEquals(1, message.path("data").path("sequence_no").asInt());
        ResponseEntity<String> read = rest.exchange(URI.create(base + "/api/sessions/" + sessionId + "/messages"), HttpMethod.GET, new HttpEntity<>(headers), String.class);
        assertEquals("postgres-persisted", json.readTree(read.getBody()).path("data").get(0).path("content").asText());
    }

    private JsonNode post(String url, String body, HttpHeaders headers) throws Exception {
        HttpHeaders actual = headers == null ? jsonHeaders() : headers;
        ResponseEntity<String> response = rest.postForEntity(url, new HttpEntity<>(body, actual), String.class);
        assertEquals(200, response.getStatusCode().value(), response.getBody());
        return json.readTree(response.getBody());
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
        return headers;
    }
}
