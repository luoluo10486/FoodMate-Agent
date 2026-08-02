package com.foodmate.bootstrap.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.foodmate.application.account.service.UserAccountService;
import com.foodmate.application.runtime.service.AgentRunCommandService;
import com.foodmate.application.runtime.service.RuntimeDlqService;
import com.foodmate.shared.id.IdGenerator;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * M1-4 阶段 D：DLQ 对账裁决。
 *
 * <p>核心断言是 ADR-0005 的那条约束——<b>进入 DLQ 不等于 AgentRun 失败</b>。 对账只写消息自身的结论，任何分支都不得改写 {@code
 * agent_runs.status}。
 */
@SpringBootTest(properties = "foodmate.runtime.dlq-reconcile-ms=3600000")
@ActiveProfiles("local")
@EnabledIfSystemProperty(named = "foodmate.local-e2e", matches = "true")
class M14DlqReconciliationE2ETest {
    @Autowired UserAccountService accounts;
    @Autowired AgentRunCommandService runs;
    @Autowired RuntimeDlqService dlq;
    @Autowired JdbcTemplate jdbc;
    @Autowired IdGenerator ids;

    private long newRun(String suffix) {
        String username =
                "m14dlq" + suffix + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        long userId =
                accounts.register(username, username + "@example.com", "password123", "DLQ")
                        .userId();
        long sessionId = accounts.createSession(userId, "m14 dlq", "agent").sessionId();
        Long runId =
                runs.createUserMessageRun(userId, sessionId, "测试 DLQ 对账", "trace-dlq").agentRunId();
        assertNotNull(runId);
        return runId;
    }

    private long insertDlq(String runId, String eventId) {
        long dlqId = ids.nextId();
        jdbc.update(
                "INSERT INTO runtime_message_dlq(dlq_id,consumer_group,source_topic,mq_message_id,run_id,event_id,error_code) "
                        + "VALUES (?,?,?,?,?,?,?)",
                dlqId,
                "foodmate-java-agent-event-v1",
                "%DLQ%foodmate-java-agent-event-v1",
                "MSG_" + dlqId,
                runId,
                eventId,
                "RUNTIME_MESSAGE_DEAD_LETTERED");
        return dlqId;
    }

    private String stateOf(long dlqId) {
        return jdbc.queryForObject(
                "SELECT reconciliation_state FROM runtime_message_dlq WHERE dlq_id=?",
                String.class,
                dlqId);
    }

    @Test
    void terminalRunResolvesWithoutTouchingRunStatus() {
        long runId = newRun("t");
        jdbc.update("UPDATE agent_runs SET status='completed' WHERE agent_run_id=?", runId);
        long dlqId = insertDlq(Long.toString(runId), "evt_" + UUID.randomUUID());

        dlq.reconcile();

        assertEquals("resolved_terminal", stateOf(dlqId));
        // 对账不得改写业务状态：终态仍是 completed，而不是被 DLQ 判成 failed。
        assertEquals(
                "completed",
                jdbc.queryForObject(
                        "SELECT status FROM agent_runs WHERE agent_run_id=?", String.class, runId));
    }

    @Test
    void eventAlreadyInInboxResolvesAsDuplicate() {
        long runId = newRun("d");
        String dispatchId =
                jdbc.queryForObject(
                        "SELECT dispatch_id FROM agent_run_dispatches WHERE agent_run_id=?",
                        String.class,
                        runId);
        String eventId = "evt_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        // 模拟「业务事务已提交但 ACK 前崩溃」：事件在 Inbox 里，消息却进了 DLQ。
        jdbc.update(
                "INSERT INTO runtime_event_inbox_v2(runtime_event_inbox_id,agent_run_id,dispatch_id,event_id,"
                        + "event_seq,event_type,occurred_at,payload_json,request_hash) "
                        + "VALUES (?,?,?,?,?,?,CURRENT_TIMESTAMP,'{}'::jsonb,?)",
                ids.nextId(),
                runId,
                dispatchId,
                eventId,
                1L,
                "run.accepted",
                "sha256:dup");
        long dlqId = insertDlq(Long.toString(runId), eventId);

        dlq.reconcile();

        assertEquals("resolved_duplicate", stateOf(dlqId));
    }

    @Test
    void activeRunAndUnparsableRunIdNeedAttentionInsteadOfAutoFailing() {
        long runId = newRun("a");
        long activeDlq = insertDlq(Long.toString(runId), "evt_" + UUID.randomUUID());
        long brokenDlq = insertDlq("not-a-run-id", "evt_" + UUID.randomUUID());

        dlq.reconcile();

        assertEquals("needs_attention", stateOf(activeDlq));
        assertEquals("needs_attention", stateOf(brokenDlq));
        // Run 仍在排队，不能因为消息进 DLQ 就判失败。
        assertEquals(
                "queued",
                jdbc.queryForObject(
                        "SELECT status FROM agent_runs WHERE agent_run_id=?", String.class, runId));
    }
}
