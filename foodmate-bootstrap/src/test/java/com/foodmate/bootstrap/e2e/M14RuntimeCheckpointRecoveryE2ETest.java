package com.foodmate.bootstrap.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.foodmate.application.account.UserAccountService;
import com.foodmate.application.runtime.AgentRunCommandService;
import com.foodmate.application.runtime.RuntimeRecoveryService;
import com.foodmate.application.runtime.V1RuntimeEventService;
import com.foodmate.shared.runtime.V1RunEvent;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/** M1-4：Java Inbox 持久化 Python checkpoint 后，由生产恢复入口创建新的 dispatch attempt。 */
@SpringBootTest(
        properties = {
            "foodmate.runtime.dispatch-poll-ms=3600000",
            "foodmate.runtime.dlq-reconcile-ms=3600000"
        })
@ActiveProfiles("local")
@EnabledIfSystemProperty(named = "foodmate.local-e2e", matches = "true")
class M14RuntimeCheckpointRecoveryE2ETest {
    @Autowired UserAccountService accounts;
    @Autowired AgentRunCommandService runs;
    @Autowired V1RuntimeEventService events;
    @Autowired RuntimeRecoveryService recovery;
    @Autowired JdbcTemplate jdbc;

    @Test
    void checkpointEventIsDurableAndRecoveryCreatesNextAttempt() throws Exception {
        String username = "m14recover_" + UUID.randomUUID().toString().replace("-", "");
        long userId =
                accounts.register(
                                username, username + "@example.com", "password123", "M14 Recovery")
                        .userId();
        long sessionId = accounts.createSession(userId, "m14 recovery", "agent").sessionId();
        long runId =
                runs.createUserMessageRun(userId, sessionId, "查询我的饮食记录", "trace-m14-recovery")
                        .agentRunId();
        String dispatchId =
                jdbc.queryForObject(
                        "SELECT dispatch_id FROM agent_run_dispatches WHERE agent_run_id=? ORDER BY attempt DESC LIMIT 1",
                        String.class,
                        runId);
        accept(runId, dispatchId, 1, "run.accepted", Map.of("status", "queued"));
        accept(runId, dispatchId, 2, "run.routed", Map.of("status", "routed"));
        accept(
                runId,
                dispatchId,
                3,
                "run.checkpoint_saved",
                Map.of(
                        "checkpoint_version",
                        1,
                        "checkpoint_digest",
                        "sha256:m14-checkpoint",
                        "budget_revision",
                        1,
                        "current_node",
                        "execution",
                        "completed_invocation_ids",
                        List.of()));

        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM runtime_event_inbox_v2 WHERE agent_run_id=? AND event_type='run.checkpoint_saved'",
                        Integer.class,
                        runId));

        RuntimeRecoveryService.RecoveryResult result =
                recovery.recoverFromPersistedCheckpoint(userId, runId);
        assertNotNull(result.dispatchId());
        assertEquals(2, result.attempt());
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM runtime_dispatch_outbox WHERE agent_run_id=? AND attempt=2",
                        Integer.class,
                        runId));
        String payload =
                jdbc.queryForObject(
                        "SELECT payload_json::text FROM runtime_dispatch_outbox WHERE agent_run_id=? AND attempt=2",
                        String.class,
                        runId);
        assertEquals(
                result.dispatchId(),
                new com.fasterxml.jackson.databind.ObjectMapper()
                        .readTree(payload)
                        .path("dispatch_id")
                        .asText());
        assertNotNull(
                new com.fasterxml.jackson.databind.ObjectMapper()
                        .readTree(payload)
                        .path("deadline_at")
                        .asText());
    }

    private void accept(
            long runId,
            String dispatchId,
            long sequence,
            String type,
            Map<String, Object> payload) {
        events.accept(
                new V1RunEvent(
                        "v1",
                        Long.toString(runId),
                        dispatchId,
                        1,
                        "evt-" + sequence + "-" + UUID.randomUUID(),
                        sequence,
                        "req-evt-" + sequence,
                        "trace-m14-recovery",
                        "sha256:event-" + sequence,
                        Instant.now(),
                        type,
                        payload));
    }
}
