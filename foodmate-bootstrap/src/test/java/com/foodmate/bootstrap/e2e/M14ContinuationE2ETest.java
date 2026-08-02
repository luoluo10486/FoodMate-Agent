package com.foodmate.bootstrap.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.foodmate.application.account.service.UserAccountService;
import com.foodmate.application.runtime.service.AgentRunCommandService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/** M1-4：waiting_user 旧 Run 由补充消息接续为 superseded 终态，并固化初始预算快照。 */
@SpringBootTest
@ActiveProfiles("local")
@EnabledIfSystemProperty(named = "foodmate.local-e2e", matches = "true")
class M14ContinuationE2ETest {
    @Autowired UserAccountService accounts;
    @Autowired AgentRunCommandService runs;
    @Autowired JdbcTemplate jdbc;

    @Test
    void clarificationMessageSupersedesWaitingRunAndLinksContinuation() {
        String username = "m14_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        long userId =
                accounts.register(username, username + "@example.com", "password123", "M14")
                        .userId();
        long sessionId = accounts.createSession(userId, "m14 continuation", "agent").sessionId();

        Long runA =
                runs.createUserMessageRun(userId, sessionId, "帮我做一周备餐计划", "trace-m14-a")
                        .agentRunId();
        assertNotNull(runA);
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM agent_run_budget_snapshots WHERE agent_run_id=? AND revision=1 AND source='initial'",
                        Integer.class,
                        runA),
                "新 Run 必须固化初始预算快照");

        // 模拟 Python 路由后进入澄清等待。
        jdbc.update("UPDATE agent_runs SET status='waiting_user' WHERE agent_run_id=?", runA);

        Long runB =
                runs.createUserMessageRun(userId, sessionId, "两个人，预算 300 元", "trace-m14-b")
                        .agentRunId();
        assertNotNull(runB);

        assertEquals(
                "superseded",
                jdbc.queryForObject(
                        "SELECT status FROM agent_runs WHERE agent_run_id=?", String.class, runA));
        assertEquals(
                runB,
                jdbc.queryForObject(
                        "SELECT superseded_by_run_id FROM agent_runs WHERE agent_run_id=?",
                        Long.class,
                        runA));
        assertEquals(
                runA,
                jdbc.queryForObject(
                        "SELECT parent_run_id FROM agent_runs WHERE agent_run_id=?",
                        Long.class,
                        runB));
        assertEquals(
                "clarification",
                jdbc.queryForObject(
                        "SELECT continuation_reason FROM agent_runs WHERE agent_run_id=?",
                        String.class,
                        runB));

        // 旧 dispatch 出局：arbitration 迁移、pending outbox 过期、SSE 收到终态事件。
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM agent_run_dispatches WHERE agent_run_id=? AND dispatch_arbitration_state='active'",
                        Integer.class,
                        runA));
        assertEquals(
                0,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM runtime_dispatch_outbox WHERE agent_run_id=? AND status='pending'",
                        Integer.class,
                        runA));
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM agent_run_sse_outbox WHERE agent_run_id=? AND event_type='run.superseded'",
                        Integer.class,
                        runA));

        // 新 Run 是普通可派发 Run：active dispatch 与 pending outbox 存在。
        assertEquals(
                1,
                jdbc.queryForObject(
                        "SELECT COUNT(*) FROM runtime_dispatch_outbox WHERE agent_run_id=? AND status='pending'",
                        Integer.class,
                        runB));
        assertNull(
                jdbc.queryForObject(
                        "SELECT superseded_by_run_id FROM agent_runs WHERE agent_run_id=?",
                        Long.class,
                        runB));
    }

    @Test
    void normalMessageDoesNotCreateContinuation() {
        String username = "m14n_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        long userId =
                accounts.register(username, username + "@example.com", "password123", "M14N")
                        .userId();
        long sessionId = accounts.createSession(userId, "m14 normal", "agent").sessionId();

        Long runId =
                runs.createUserMessageRun(userId, sessionId, "今天晚饭吃什么", "trace-m14-n").agentRunId();
        assertNull(
                jdbc.queryForObject(
                        "SELECT parent_run_id FROM agent_runs WHERE agent_run_id=?",
                        Long.class,
                        runId));
        assertNull(
                jdbc.queryForObject(
                        "SELECT continuation_reason FROM agent_runs WHERE agent_run_id=?",
                        String.class,
                        runId));
    }

    @Test
    void terminalRunStatusIsNotRolledBackByLateProjection() {
        String username = "m14t_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        long userId =
                accounts.register(username, username + "@example.com", "password123", "M14T")
                        .userId();
        long sessionId = accounts.createSession(userId, "m14 terminal", "agent").sessionId();
        Long runId =
                runs.createUserMessageRun(userId, sessionId, "记录午餐", "trace-m14-t").agentRunId();

        jdbc.update("UPDATE agent_runs SET status='completed' WHERE agent_run_id=?", runId);
        // 数据库层：终态后 continuation 入口必须拒绝（completed 不是 waiting_user）。
        assertThrows(
                Exception.class,
                () ->
                        jdbc.update(
                                "UPDATE agent_runs SET status='routing_invalid' WHERE agent_run_id=?",
                                runId),
                "非法状态值必须被 CHECK 约束拒绝");
        assertEquals(
                "completed",
                jdbc.queryForObject(
                        "SELECT status FROM agent_runs WHERE agent_run_id=?", String.class, runId));
    }
}
