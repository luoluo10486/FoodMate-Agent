package com.foodmate.bootstrap.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.foodmate.shared.security.ServiceJwt;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** M1-5: real PostgreSQL HTTP regression for the food_log_writer Tool Gateway. */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "foodmate.runtime.transport=http",
            "foodmate.runtime.admission.enabled=false",
            "spring.data.redis.url=redis://:foodmate-redis-change-me@localhost:6380"
        })
@ActiveProfiles("local")
@EnabledIfSystemProperty(named = "foodmate.local-http-e2e", matches = "true")
class M15FoodLogWriterHttpE2ETest extends M15FoodLogWriterE2ETestSupport {
    private static final KeyPair KEY_PAIR = generateKeyPair();

    @Autowired TestRestTemplate http;
    @LocalServerPort int port;

    @DynamicPropertySource
    static void serviceJwtProperties(DynamicPropertyRegistry registry) {
        registry.add("foodmate.runtime.service-jwt.enabled", () -> "true");
        registry.add(
                "foodmate.runtime.service-jwt.java-private-key",
                M15FoodLogWriterHttpE2ETest::privateKey);
        registry.add(
                "foodmate.runtime.service-jwt.java-public-key",
                M15FoodLogWriterHttpE2ETest::publicKey);
        registry.add(
                "foodmate.runtime.service-jwt.python-public-key",
                M15FoodLogWriterHttpE2ETest::publicKey);
        registry.add("foodmate.runtime.service-jwt.java-kid", () -> "java-test");
    }

    @Test
    void httpWriterProposalUsesServiceJwtAndDoesNotDuplicateOnReplay() throws Exception {
        Fixture fixture = fixture("http-create");
        var input = createInput("M1-5 HTTP writer E2E");
        String approvalKey = "m15-http-create-" + fixture.suffix();
        var proposal = propose(fixture, "create", null, input, approvalKey, true);
        WriterRequest request = writerRequest(fixture, proposal, input, approvalKey, "http-create");

        List<TransportResult> results = submit(request, 2);

        assertResult(results.getFirst(), "success", null);
        assertSameResult(results.getFirst(), results.get(1));
        String foodLogId = results.getFirst().rows().get(0).path("food_log_id").asText();
        assertTrue(!foodLogId.isBlank());
        assertEquals(
                1,
                count(
                        "SELECT COUNT(*) FROM food_logs WHERE food_log_id=?",
                        Long.parseLong(foodLogId)));
        assertEquals(
                1, count("SELECT COUNT(*) FROM food_logs WHERE agent_run_id=?", fixture.runId()));
        assertEquals(
                1,
                count(
                        "SELECT COUNT(*) FROM food_log_items WHERE food_log_id=? AND nutrition_status='matched' AND nutrition_food_id=510001",
                        Long.parseLong(foodLogId)));
    }

    @Test
    void httpRejectedProposalDoesNotWrite() throws Exception {
        runRejected(this::submit);
    }

    @Test
    void httpFailedProposalRollsBackAndWritesFailureAudit() throws Exception {
        runFailed(this::submit);
    }

    @Test
    void httpSupersededProposalCannotExecute() throws Exception {
        runSuperseded(this::submit);
    }

    @Test
    void httpWriterUpdatesFoodLog() throws Exception {
        runUpdate(this::submit);
    }

    @Test
    void httpWriterDeletesFoodLog() throws Exception {
        runDelete(this::submit);
    }

    @Test
    void httpWriterRestoresFoodLog() throws Exception {
        runRestore(this::submit);
    }

    @Test
    void httpWriterRejectsStaleRevision() throws Exception {
        runRevisionConflict(this::submit);
    }

    @Test
    void httpSuccessfulProposalReplayIsIdempotent() throws Exception {
        runIdempotentReplay(this::submit);
    }

    @Test
    void httpWriterUsesReviewedFoodPortionConversion() throws Exception {
        runUnitConversion(this::submit);
    }

    @Test
    void httpWriterKeepsUnsupportedFoodPortionPending() throws Exception {
        runUnitConversionPending(this::submit);
    }

    private List<TransportResult> submit(WriterRequest request, int deliveries) {
        Map<String, Object> body =
                Map.of(
                        "schema_version",
                        "v1",
                        "proposal_id",
                        request.proposalId(),
                        "run_id",
                        Long.toString(request.runId()),
                        "proposal_type",
                        "tool",
                        "requires_confirmation",
                        true,
                        "tool_name",
                        "food_log_writer",
                        "confirmation_ref",
                        Long.toString(request.approvalRequestId()),
                        "input",
                        request.input(),
                        "payload",
                        Map.of(
                                "invocation_id",
                                request.invocationId(),
                                "idempotency_key",
                                request.idempotencyKey()));
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(
                ServiceJwt.sign(
                        privateKey(),
                        "foodmate-agent-runtime",
                        "foodmate-control-plane",
                        "runtime:proposal",
                        "python-test",
                        600));
        headers.set("X-Contract-Version", "v1");

        List<TransportResult> results = new ArrayList<>();
        for (int i = 0; i < deliveries; i++) {
            JsonNode response = post(body, headers);
            JsonNode data = response.path("data");
            results.add(
                    new TransportResult(
                            data.path("status").asText(), errorCode(data), data.path("rows")));
        }
        return results;
    }

    private JsonNode post(Map<String, Object> body, HttpHeaders headers) {
        ResponseEntity<JsonNode> response =
                http.exchange(
                        "http://localhost:" + port + "/foodmate/internal/v1/proposals",
                        HttpMethod.POST,
                        new HttpEntity<>(body, headers),
                        JsonNode.class);
        assertEquals(200, response.getStatusCode().value());
        return response.getBody();
    }

    private static String errorCode(JsonNode data) {
        JsonNode snakeCase = data.get("error_code");
        if (snakeCase != null && !snakeCase.isNull()) return snakeCase.asText();
        JsonNode camelCase = data.get("errorCode");
        return camelCase == null || camelCase.isNull() ? null : camelCase.asText();
    }

    private static String privateKey() {
        return Base64.getEncoder().encodeToString(KEY_PAIR.getPrivate().getEncoded());
    }

    private static String publicKey() {
        return Base64.getEncoder().encodeToString(KEY_PAIR.getPublic().getEncoded());
    }

    private static KeyPair generateKeyPair() {
        try {
            return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        } catch (Exception exception) {
            throw new IllegalStateException("unable to generate test service key", exception);
        }
    }
}
