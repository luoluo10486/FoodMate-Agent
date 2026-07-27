package com.foodmate.bootstrap.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodmate.application.account.UserAccountService;
import com.foodmate.application.runtime.AgentRunCommandService;
import com.foodmate.application.runtime.RuntimeDispatchPublisher;
import com.foodmate.gateway.MqConsumeDecision;
import com.foodmate.gateway.RocketMqConsumerContainer;
import com.foodmate.gateway.RocketMqSettings;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * M1-4 阶段 D：Outbox -> RocketMQ -> 消费端的真实闭环。
 *
 * <p>需要本地 Broker 与 PostgreSQL，用独立开关打开：
 * {@code -Dfoodmate.local-mq-e2e=true}。
 */
@SpringBootTest(properties = {
        "foodmate.runtime.transport=rocketmq",
        // Relay 由本测试显式驱动，避免定时任务与断言竞争。
        "foodmate.runtime.dispatch-poll-ms=3600000",
        "foodmate.runtime.dlq-reconcile-ms=3600000"
})
@ActiveProfiles("local")
@EnabledIfSystemProperty(named = "foodmate.local-mq-e2e", matches = "true")
class M14RocketMqTransportE2ETest {
    @Autowired UserAccountService accounts;
    @Autowired AgentRunCommandService runs;
    @Autowired RuntimeDispatchPublisher publisher;
    @Autowired RocketMqSettings settings;
    @Autowired JdbcTemplate jdbc;

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void outboxIsPublishedToBrokerAndConsumedWithIdenticalEnvelope() throws Exception {
        String username = "m14mq_" + UUID.randomUUID().toString().replace("-", "").substring(0, 18);
        long userId = accounts.register(username, username + "@example.com", "password123", "M14MQ").userId();
        long sessionId = accounts.createSession(userId, "m14 rocketmq", "agent").sessionId();
        Long runId = runs.createUserMessageRun(userId, sessionId, "帮我规划今天的午餐", "trace-m14-mq").agentRunId();
        assertNotNull(runId);

        String expectedHash = jdbc.queryForObject(
                "SELECT request_hash FROM runtime_dispatch_outbox WHERE agent_run_id=?", String.class, runId);
        String expectedDispatch = jdbc.queryForObject(
                "SELECT dispatch_id FROM runtime_dispatch_outbox WHERE agent_run_id=?", String.class, runId);

        // 消费组必须先于发布启动：新组从最新位点消费，晚启动会漏掉这条消息。
        // Broker 关闭了 autoCreateSubscriptionGroup，只能用 init-topics.sh 预建的自测组；
        // 用独立组才不会挪动 Python 正式消费组的位点。
        CountDownLatch received = new CountDownLatch(1);
        Map<String, String> captured = new ConcurrentHashMap<>();
        RocketMqConsumerContainer consumer = RocketMqConsumerContainer.orderly(
                settings.nameServer(), "foodmate-selftest-v1",
                settings.commandTopic(), 1,
                (body, context) -> {
                    if (!Long.toString(runId).equals(context.properties().get("foodmate_run_id"))) {
                        return MqConsumeDecision.ACK; // 其他用例的消息，跳过
                    }
                    captured.put("body", body);
                    captured.putAll(context.properties());
                    if (context.messageKey() != null) captured.put("key", context.messageKey());
                    received.countDown();
                    return MqConsumeDecision.ACK;
                });
        consumer.start();
        try {
            Thread.sleep(3000); // 等待 rebalance 完成，否则消息落在未分配的队列上
            publisher.publishPending();

            assertTrue(received.await(30, TimeUnit.SECONDS), "命令必须在 30 秒内到达消费端");

            // Outbox 只有在 Broker 持久化确认后才允许标记 published（ADR-0005）。
            assertEquals("published", jdbc.queryForObject(
                    "SELECT status FROM runtime_dispatch_outbox WHERE agent_run_id=?", String.class, runId));
            assertEquals("rocketmq", jdbc.queryForObject(
                    "SELECT transport FROM runtime_dispatch_outbox WHERE agent_run_id=?", String.class, runId));
            assertNotNull(jdbc.queryForObject(
                    "SELECT mq_message_id FROM runtime_dispatch_outbox WHERE agent_run_id=?", String.class, runId),
                    "published 必须记录 Broker 消息 ID 以便与 DLQ 对账");

            // 消息体必须是 Outbox 里持久化的原始 envelope，不能被重新拼装。
            var node = mapper.readTree(captured.get("body"));
            assertEquals(expectedHash, node.get("request_hash").asText());
            assertEquals(expectedDispatch, node.get("dispatch_id").asText());
            assertEquals(Long.toString(runId), node.get("run_id").asText());
            assertEquals("v1", node.get("schema_version").asText());

            // run_id 既是顺序键也是查询 key，消费端在解析消息体前就能用。
            assertEquals(Long.toString(runId), captured.get("key"));
            assertEquals("RunCommand", captured.get("foodmate_message_type"));
            assertEquals(expectedHash, captured.get("foodmate_request_hash"));
        } finally {
            consumer.close();
        }
    }
}
