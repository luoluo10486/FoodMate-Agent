package com.foodmate.bootstrap.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.foodmate.application.account.service.UserAccountService;
import com.foodmate.application.food.service.ApprovalService;
import com.foodmate.application.runtime.service.AgentRunCommandService;
import com.foodmate.shared.security.ServiceJwt;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** M1-5：food_log_writer 内部 Proposal HTTP 入口的真实 PostgreSQL 回归。 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "foodmate.runtime.transport=http",
            "foodmate.runtime.admission.enabled=false",
            "spring.data.redis.url=redis://:foodmate-redis-change-me@localhost:6380"
        })
@ActiveProfiles("local")
@EnabledIfSystemProperty(named = "foodmate.local-http-e2e", matches = "true")
class M15FoodLogWriterHttpE2ETest {
    private static final KeyPair KEY_PAIR = generateKeyPair();
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Autowired UserAccountService accounts;
    @Autowired AgentRunCommandService runs;
    @Autowired ApprovalService approvals;
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
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
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String username = "m15http_" + suffix.substring(0, 16);
        long userId =
                accounts.register(username, username + "@example.com", "password123", "M1-5 HTTP")
                        .userId();
        long sessionId = accounts.createSession(userId, "m1-5 http", "agent").sessionId();
        long runId =
                runs.createUserMessageRun(userId, sessionId, "记录 HTTP 午餐", "trace-m15-http")
                        .agentRunId();

        ObjectNode input = mapper.createObjectNode();
        input.put("meal_time", Instant.now().toString());
        input.put("meal_type", "lunch");
        input.put("notes", "M1-5 HTTP writer E2E");
        input.putArray("items").addObject().put("name", "rice").put("amount", 100).put("unit", "g");

        String approvalKey = "m15-http-" + suffix;
        ApprovalService.ProposalView proposal =
                approvals.propose(
                        userId,
                        new ApprovalService.ProposalCommand(
                                sessionId,
                                runId,
                                "create",
                                "food_log",
                                null,
                                input,
                                approvalKey,
                                300));
        approvals.confirm(userId, proposal.approvalRequestId(), input);

        String proposalId = "m15-http-proposal-" + suffix;
        Map<String, Object> body =
                Map.of(
                        "schema_version",
                        "v1",
                        "proposal_id",
                        proposalId,
                        "run_id",
                        Long.toString(runId),
                        "proposal_type",
                        "tool",
                        "tool_name",
                        "food_log_writer",
                        "confirmation_ref",
                        Long.toString(proposal.approvalRequestId()),
                        "input",
                        input,
                        "payload",
                        Map.of(
                                "invocation_id",
                                "m15-http-invocation-" + suffix,
                                "idempotency_key",
                                approvalKey));
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

        JsonNode first = post(body, headers);
        JsonNode replay = post(body, headers);

        assertEquals("success", first.path("data").path("status").asText());
        assertEquals("success", replay.path("data").path("status").asText());
        String foodLogId = first.path("data").path("rows").get(0).path("food_log_id").asText();
        assertTrue(!foodLogId.isBlank());
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM food_logs WHERE food_log_id=?",
                        Integer.class,
                        Long.parseLong(foodLogId)));
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM food_logs WHERE agent_run_id=?",
                        Integer.class,
                        runId));
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM food_log_items WHERE food_log_id=? AND nutrition_status='matched' AND nutrition_food_id=510001",
                        Integer.class,
                        Long.parseLong(foodLogId)));
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
