package com.foodmate.bootstrap.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodmate.application.runtime.service.RuntimeDlqReplayService;
import com.foodmate.application.runtime.service.impl.ModelGovernanceAdminServiceImpl;
import com.foodmate.shared.id.IdGenerator;
import java.time.Instant;
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

/** M11 模型治理和 DLQ 重放真实 HTTP 闭环；只使用随机临时数据。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("local")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestPropertySource(
        properties = {
            "foodmate.storage.endpoint=http://localhost:9000",
            "foodmate.storage.access-key=foodmate-local",
            "foodmate.storage.secret-key=foodmate-local-secret-change-me-20260722",
            "foodmate.storage.bucket=foodmate-private",
            "foodmate.runtime.dlq-replay-poll-ms=3600000"
        })
@EnabledIfSystemProperty(named = "foodmate.local-e2e", matches = "true")
class M11ModelGovernanceAndDlqReplayE2ETest {
    @LocalServerPort int port;
    @Autowired TestRestTemplate rest;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired IdGenerator ids;

    private final Set<Long> userIds = new HashSet<>();
    private final Set<Long> providerIds = new HashSet<>();
    private final Set<Long> priceIds = new HashSet<>();
    private final Set<Long> budgetIds = new HashSet<>();
    private final Set<Long> dlqIds = new HashSet<>();
    private final Set<Long> replayIds = new HashSet<>();
    private final Set<String> providerCodes = new HashSet<>();
    private final Set<String> priceVersions = new HashSet<>();
    private final Set<String> budgetVersions = new HashSet<>();

    @AfterEach
    void cleanTemporaryFacts() {
        for (long replayId : replayIds) {
            jdbc.update("DELETE FROM runtime_dlq_replay_outbox WHERE replay_id=?", replayId);
        }
        for (long dlqId : dlqIds) {
            jdbc.update("DELETE FROM runtime_dlq_replay_outbox WHERE dlq_id=?", dlqId);
            jdbc.update("DELETE FROM runtime_message_dlq WHERE dlq_id=?", dlqId);
        }
        for (long priceId : priceIds) {
            jdbc.update("DELETE FROM model_price_versions WHERE price_version_id=?", priceId);
        }
        for (String priceVersion : priceVersions) {
            jdbc.update(
                    "DELETE FROM model_price_versions WHERE price_version=? AND provider_code LIKE 'e2e_%'",
                    priceVersion);
        }
        for (long budgetId : budgetIds) {
            jdbc.update("DELETE FROM model_budget_policies WHERE budget_policy_id=?", budgetId);
        }
        for (String budgetVersion : budgetVersions) {
            jdbc.update(
                    "DELETE FROM model_budget_policies WHERE policy_version=? AND policy_key LIKE 'e2e_%'",
                    budgetVersion);
        }
        for (String providerCode : providerCodes) {
            jdbc.update("DELETE FROM model_catalog WHERE provider_code=?", providerCode);
            jdbc.update("DELETE FROM model_providers WHERE provider_code=?", providerCode);
        }
        for (long providerId : providerIds) {
            jdbc.update("DELETE FROM model_providers WHERE provider_id=?", providerId);
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
    void modelGovernanceRealHttpHonorsRolesRevisionsAndIdempotency() throws Exception {
        String suffix = suffix();
        String superadminName = "governance_superadmin_" + suffix;
        String adminName = "governance_admin_" + suffix;
        long superadminId = register(superadminName);
        long adminId = register(adminName);
        userIds.addAll(Set.of(superadminId, adminId));
        jdbc.update("UPDATE users SET role='superadmin' WHERE user_id=?", superadminId);
        jdbc.update("UPDATE users SET role='admin' WHERE user_id=?", adminId);

        HttpHeaders superadmin = login(superadminName);
        HttpHeaders admin = login(adminName);
        long providerId = ids.nextId();
        providerIds.add(providerId);
        String providerCode = "e2e_" + suffix;
        providerCodes.add(providerCode);
        jdbc.update(
                "INSERT INTO model_providers(provider_id,provider_code,display_name,status,endpoint_config_key,capability_json,created_by,updated_by,revision) VALUES (?,?,?,'active',?,'{}'::jsonb,?,?,1)",
                providerId,
                providerCode,
                "E2E Provider",
                "e2e.provider.config",
                superadminId,
                superadminId);

        ResponseEntity<String> forbidden =
                patch(
                        "/api/admin/model-governance/providers/" + providerCode + "/status",
                        admin,
                        "governance-admin-forbidden-" + suffix,
                        providerStatusBody(providerCode, "disabled", 1));
        assertEquals(403, forbidden.getStatusCode().value(), forbidden.getBody());

        String providerKey = "governance-provider-" + suffix;
        ResponseEntity<String> disabled =
                patch(
                        "/api/admin/model-governance/providers/" + providerCode + "/status",
                        superadmin,
                        providerKey,
                        providerStatusBody(providerCode, "disabled", 1));
        assertEquals(200, disabled.getStatusCode().value(), disabled.getBody());
        assertEquals("disabled", data(disabled).path("version").asText());
        assertEquals(
                "disabled",
                jdbc.queryForObject(
                        "SELECT status FROM model_providers WHERE provider_id=?",
                        String.class,
                        providerId));

        ResponseEntity<String> providerReplay =
                patch(
                        "/api/admin/model-governance/providers/" + providerCode + "/status",
                        superadmin,
                        providerKey,
                        providerStatusBody(providerCode, "disabled", 1));
        assertEquals(200, providerReplay.getStatusCode().value(), providerReplay.getBody());
        assertEquals(
                data(disabled).path("resource_id").asLong(),
                data(providerReplay).path("resource_id").asLong());
        assertEquals(
                2L,
                jdbc.queryForObject(
                        "SELECT revision FROM model_providers WHERE provider_id=?",
                        Long.class,
                        providerId));

        String modelName = "e2e-model-" + suffix;
        String priceVersion = "price-" + suffix;
        priceVersions.add(priceVersion);
        String priceKey = "governance-price-" + suffix;
        String priceBody = priceBody(providerCode, modelName, priceVersion);
        ResponseEntity<String> price =
                post("/api/admin/model-governance/prices", superadmin, priceKey, priceBody);
        assertEquals(200, price.getStatusCode().value(), price.getBody());
        long priceId = data(price).path("resource_id").asLong();
        priceIds.add(priceId);
        ResponseEntity<String> priceReplay =
                post("/api/admin/model-governance/prices", superadmin, priceKey, priceBody);
        assertEquals(200, priceReplay.getStatusCode().value(), priceReplay.getBody());
        assertEquals(priceId, data(priceReplay).path("resource_id").asLong());
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM model_price_versions WHERE provider_code=? AND model_name=? AND price_version=?",
                        Integer.class,
                        providerCode,
                        modelName,
                        priceVersion));

        String policyKey = "e2e_" + suffix;
        String policyVersion = "budget-" + suffix;
        budgetVersions.add(policyVersion);
        String budgetKey = "governance-budget-" + suffix;
        ResponseEntity<String> budget =
                post(
                        "/api/admin/model-governance/budgets",
                        superadmin,
                        budgetKey,
                        budgetBody(policyKey, policyVersion));
        assertEquals(200, budget.getStatusCode().value(), budget.getBody());
        long budgetId = data(budget).path("resource_id").asLong();
        budgetIds.add(budgetId);

        ResponseEntity<String> view = get("/api/admin/model-governance", admin);
        assertEquals(200, view.getStatusCode().value(), view.getBody());
        JsonNode governance = data(view);
        assertTrue(hasValue(governance.path("providers"), "provider_code", providerCode));
        assertTrue(hasValue(governance.path("prices"), "price_version", priceVersion));
        assertTrue(hasValue(governance.path("budgets"), "policy_version", policyVersion));
        assertFalse(view.getBody().contains("api_key"));
        assertTrue(
                jdbc.queryForObject(
                                "SELECT COUNT(*) FROM operation_audits WHERE operator_id=? AND action LIKE 'model.%'",
                                Integer.class, superadminId)
                        >= 3);
    }

    @Test
    void dlqReplayRealHttpIsAuditedIdempotentAndAsynchronous() throws Exception {
        String suffix = suffix();
        String superadminName = "dlq_superadmin_" + suffix;
        String adminName = "dlq_admin_" + suffix;
        long superadminId = register(superadminName);
        long adminId = register(adminName);
        userIds.addAll(Set.of(superadminId, adminId));
        jdbc.update("UPDATE users SET role='superadmin' WHERE user_id=?", superadminId);
        jdbc.update("UPDATE users SET role='admin' WHERE user_id=?", adminId);

        HttpHeaders superadmin = login(superadminName);
        HttpHeaders admin = login(adminName);
        long dlqId = ids.nextId();
        dlqIds.add(dlqId);
        String eventId = "e2e-event-" + suffix;
        String messageId = "e2e-message-" + suffix;
        String runId = "e2e-run-" + dlqId;
        String dispatchId = "e2e-dispatch-" + dlqId;
        String payload = "{\"event_type\":\"run.completed\",\"run_id\":\"" + runId + "\"}";
        jdbc.update(
                "INSERT INTO runtime_message_dlq(dlq_id,consumer_group,source_topic,mq_message_id,message_key,run_id,dispatch_id,attempt,event_id,event_seq,request_hash,reconsume_times,error_code,last_error,raw_payload_json,raw_payload_text,reconciliation_state,reconciled_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,'RUNTIME_MESSAGE_DEAD_LETTERED',?,CAST(? AS jsonb),?,'needs_attention',CURRENT_TIMESTAMP)",
                dlqId,
                "foodmate-java-agent-event-v1",
                "foodmate-agent-event-v1",
                messageId,
                "e2e-key-" + suffix,
                runId,
                dispatchId,
                3,
                eventId,
                1L,
                "sha256:e2e-" + suffix,
                3,
                "e2e local replay",
                payload,
                payload);

        String confirmation = RuntimeDlqReplayService.confirmationDigest(dlqId);
        String body = "{\"confirmed\":true,\"confirmationDigest\":\"" + confirmation + "\"}";
        ResponseEntity<String> forbidden =
                post(
                        "/api/admin/dlq/" + dlqId + "/replay",
                        admin,
                        "dlq-admin-forbidden-" + suffix,
                        body);
        assertEquals(403, forbidden.getStatusCode().value(), forbidden.getBody());

        String idempotencyKey = "dlq-replay-" + suffix;
        ResponseEntity<String> queued =
                post("/api/admin/dlq/" + dlqId + "/replay", superadmin, idempotencyKey, body);
        assertEquals(200, queued.getStatusCode().value(), queued.getBody());
        JsonNode queuedData = data(queued);
        long replayId = queuedData.path("replay_id").asLong();
        replayIds.add(replayId);
        assertTrue(replayId > 0);
        assertEquals("queued", queuedData.path("status").asText());
        assertEquals(messageId, queuedData.path("original_message_id").asText());
        assertFalse(queuedData.has("payload"));

        ResponseEntity<String> replayed =
                post("/api/admin/dlq/" + dlqId + "/replay", superadmin, idempotencyKey, body);
        assertEquals(200, replayed.getStatusCode().value(), replayed.getBody());
        assertEquals(replayId, data(replayed).path("replay_id").asLong());

        ResponseEntity<String> activeConflict =
                post(
                        "/api/admin/dlq/" + dlqId + "/replay",
                        superadmin,
                        "dlq-replay-second-" + suffix,
                        body);
        assertEquals(409, activeConflict.getStatusCode().value(), activeConflict.getBody());
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM runtime_dlq_replay_outbox WHERE dlq_id=?",
                        Integer.class,
                        dlqId));
        assertEquals(
                "pending",
                jdbc.queryForObject(
                        "SELECT status FROM runtime_dlq_replay_outbox WHERE replay_id=?",
                        String.class,
                        replayId));
        assertEquals(
                "needs_attention",
                jdbc.queryForObject(
                        "SELECT reconciliation_state FROM runtime_message_dlq WHERE dlq_id=?",
                        String.class,
                        dlqId));
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
        return rest.exchange(
                url(path), HttpMethod.GET, new HttpEntity<>(sourceHeaders), String.class);
    }

    private ResponseEntity<String> patch(
            String path, HttpHeaders sourceHeaders, String idempotencyKey, String body) {
        return exchange(path, HttpMethod.PATCH, sourceHeaders, idempotencyKey, body);
    }

    private ResponseEntity<String> post(
            String path, HttpHeaders sourceHeaders, String idempotencyKey, String body) {
        return exchange(path, HttpMethod.POST, sourceHeaders, idempotencyKey, body);
    }

    private ResponseEntity<String> exchange(
            String path,
            HttpMethod method,
            HttpHeaders sourceHeaders,
            String idempotencyKey,
            String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.putAll(sourceHeaders);
        headers.set("Idempotency-Key", idempotencyKey);
        return rest.exchange(url(path), method, new HttpEntity<>(body, headers), String.class);
    }

    private JsonNode data(ResponseEntity<String> response) throws Exception {
        assertNotNull(response.getBody());
        return json.readTree(response.getBody()).path("data");
    }

    private String providerStatusBody(String providerCode, String status, long revision) {
        String digest =
                ModelGovernanceAdminServiceImpl.confirmationDigest(
                        "model.provider.status.update", providerCode, status, revision);
        return "{\"status\":\""
                + status
                + "\",\"revision\":"
                + revision
                + ",\"confirmed\":true,\"confirmationDigest\":\""
                + digest
                + "\"}";
    }

    private String priceBody(String providerCode, String modelName, String priceVersion) {
        String target = providerCode + ":" + modelName + ":" + priceVersion;
        String digest =
                ModelGovernanceAdminServiceImpl.confirmationDigest(
                        "model.price.create", target, priceVersion, 1);
        return "{\"providerCode\":\""
                + providerCode
                + "\",\"modelName\":\""
                + modelName
                + "\",\"priceVersion\":\""
                + priceVersion
                + "\",\"inputPricePerMillion\":\"1.2\",\"outputPricePerMillion\":\"2.4\","
                + "\"currency\":\"CNY\",\"effectiveAt\":\""
                + Instant.now().plusSeconds(60).toString()
                + "\",\"revision\":1,\"confirmed\":true,\"confirmationDigest\":\""
                + digest
                + "\"}";
    }

    private String budgetBody(String policyKey, String policyVersion) {
        String target = policyKey + ":" + policyVersion;
        String digest =
                ModelGovernanceAdminServiceImpl.confirmationDigest(
                        "model.budget.create", target, policyVersion, 1);
        return "{\"policyKey\":\""
                + policyKey
                + "\",\"scene\":\"chat\",\"scopeType\":\"global\","
                + "\"maxTotalTokens\":50000,\"maxCostCny\":\"10.00\",\"maxModelCalls\":10,"
                + "\"maxStepRetries\":2,\"windowType\":\"run\",\"policyVersion\":\""
                + policyVersion
                + "\",\"revision\":1,\"confirmed\":true,\"confirmationDigest\":\""
                + digest
                + "\"}";
    }

    private boolean hasValue(JsonNode values, String field, String expected) {
        for (JsonNode value : values) {
            if (expected.equals(value.path(field).asText())) return true;
        }
        return false;
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
        return UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }
}
