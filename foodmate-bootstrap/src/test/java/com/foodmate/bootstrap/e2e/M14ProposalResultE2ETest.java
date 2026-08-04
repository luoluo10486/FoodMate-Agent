package com.foodmate.bootstrap.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodmate.application.account.service.UserAccountService;
import com.foodmate.application.runtime.messaging.MqConsumeDecision;
import com.foodmate.application.runtime.service.AgentRunCommandService;
import com.foodmate.infrastructure.messaging.rocketmq.RocketMqConsumerContainer;
import com.foodmate.infrastructure.messaging.rocketmq.RocketMqSettings;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.Message;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/** M1-4：Proposal -> Java Tool Gateway -> Result 的本地真实 RocketMQ 闭环。 */
@SpringBootTest(
        properties = {
            "foodmate.runtime.transport=rocketmq",
            "foodmate.runtime.dispatch-poll-ms=3600000",
            "foodmate.runtime.dlq-reconcile-ms=3600000"
        })
@ActiveProfiles("local")
@EnabledIfSystemProperty(named = "foodmate.local-mq-e2e", matches = "true")
class M14ProposalResultE2ETest {
    @Autowired UserAccountService accounts;
    @Autowired AgentRunCommandService runs;
    @Autowired DefaultMQProducer producer;
    @Autowired RocketMqSettings settings;
    @Autowired JdbcTemplate jdbc;

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void proposalIsExecutedAuditedAndResultIsPublishedIdempotently() throws Exception {
        String username =
                "m14proposal_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        long userId =
                accounts.register(
                                username, username + "@example.com", "password123", "M14 Proposal")
                        .userId();
        long sessionId = accounts.createSession(userId, "m14 proposal", "agent").sessionId();
        long runId =
                runs.createUserMessageRun(userId, sessionId, "查询我的记录", "trace-m14-proposal")
                        .agentRunId();
        String proposalId = "proposal-" + UUID.randomUUID();
        String requestHash = "sha256:m14-proposal" + UUID.randomUUID().toString().replace("-", "");
        String body =
                mapper.writeValueAsString(
                        Map.of(
                                "schema_version",
                                "v1",
                                "proposal_id",
                                proposalId,
                                "run_id",
                                Long.toString(runId),
                                "proposal_type",
                                "sql_read",
                                "request_hash",
                                requestHash,
                                "requires_confirmation",
                                false,
                                "payload",
                                Map.of(
                                        "statement",
                                        "SELECT 1",
                                        "invocation_id",
                                        "invocation-" + proposalId)));

        CountDownLatch received = new CountDownLatch(1);
        Map<String, String> resultBody = new ConcurrentHashMap<>();
        RocketMqConsumerContainer resultConsumer =
                RocketMqConsumerContainer.concurrent(
                        // 使用 Compose 已预创建的 Python Result group；本测试不启动 Python consumer，避免竞争消费。
                        settings.nameServer(),
                        "foodmate-python-agent-result-v1",
                        settings.resultTopic(),
                        1,
                        (result, context) -> {
                            try {
                                JsonNode node = mapper.readTree(result);
                                if (proposalId.equals(node.path("proposal_id").asText())) {
                                    resultBody.put("body", result);
                                    received.countDown();
                                }
                            } catch (Exception ignored) {
                                return MqConsumeDecision.REJECT;
                            }
                            return MqConsumeDecision.ACK;
                        });
        resultConsumer.start();
        try {
            Thread.sleep(2500);
            Message message =
                    new Message(settings.proposalTopic(), body.getBytes(StandardCharsets.UTF_8));
            message.setKeys(Long.toString(runId));
            message.putUserProperty("foodmate_message_type", "ToolProposal");
            message.putUserProperty("foodmate_proposal_id", proposalId);
            message.putUserProperty("foodmate_request_hash", requestHash);
            assertNotNull(producer.send(message));
            assertTrue(received.await(30, TimeUnit.SECONDS), "Proposal Result 必须在 30 秒内返回");

            JsonNode result = mapper.readTree(resultBody.get("body"));
            assertEquals("succeeded", result.path("status").asText());
            assertEquals(requestHash, result.path("request_hash").asText());
            assertEquals(
                    1,
                    jdbc.queryForObject(
                            "SELECT COUNT(*) FROM runtime_tool_proposal_inbox WHERE proposal_id=?",
                            Integer.class,
                            proposalId));
            assertEquals(
                    1,
                    jdbc.queryForObject(
                            "SELECT COUNT(*) FROM sql_query_audits WHERE trace_id=?",
                            Integer.class,
                            "proposal:" + proposalId));
        } finally {
            resultConsumer.close();
        }
    }

    @Test
    void toolFailureProducesAuditedFailedResultAndDuplicateProposalDoesNotReexecute()
            throws Exception {
        String username =
                "m14proposal_failure_"
                        + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        long userId =
                accounts.register(
                                username,
                                username + "@example.com",
                                "password123",
                                "M14 Proposal Failure")
                        .userId();
        long sessionId =
                accounts.createSession(userId, "m14 proposal failure", "agent").sessionId();
        long runId =
                runs.createUserMessageRun(userId, sessionId, "故障注入", "trace-m14-proposal-failure")
                        .agentRunId();
        String proposalId = "proposal-failure-" + UUID.randomUUID();
        String requestHash = "sha256:m14-failure-" + UUID.randomUUID().toString().replace("-", "");
        String body =
                mapper.writeValueAsString(
                        Map.of(
                                "schema_version",
                                "v1",
                                "proposal_id",
                                proposalId,
                                "run_id",
                                Long.toString(runId),
                                "proposal_type",
                                "sql_read",
                                "request_hash",
                                requestHash,
                                "requires_confirmation",
                                false,
                                "payload",
                                Map.of(
                                        "statement",
                                        "SELECT * FROM table_that_does_not_exist",
                                        "invocation_id",
                                        "invocation-" + proposalId)));
        CountDownLatch received = new CountDownLatch(1);
        RocketMqConsumerContainer resultConsumer =
                RocketMqConsumerContainer.concurrent(
                        settings.nameServer(),
                        "foodmate-m14-failure-" + UUID.randomUUID(),
                        settings.resultTopic(),
                        1,
                        (result, context) -> {
                            try {
                                if (proposalId.equals(
                                        mapper.readTree(result).path("proposal_id").asText()))
                                    received.countDown();
                            } catch (Exception exception) {
                                return MqConsumeDecision.REJECT;
                            }
                            return MqConsumeDecision.ACK;
                        });
        resultConsumer.start();
        try {
            Thread.sleep(2500);
            Message message =
                    new Message(settings.proposalTopic(), body.getBytes(StandardCharsets.UTF_8));
            message.setKeys(Long.toString(runId));
            message.putUserProperty("foodmate_message_type", "ToolProposal");
            message.putUserProperty("foodmate_proposal_id", proposalId);
            message.putUserProperty("foodmate_request_hash", requestHash);
            producer.send(message);
            producer.send(
                    new Message(settings.proposalTopic(), body.getBytes(StandardCharsets.UTF_8)));
            // Inbox completed 只会在 Tool 执行、审计和 Result 发布都成功后写入，作为本次故障注入的权威完成事实。
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
            String status = null;
            while (System.nanoTime() < deadline) {
                status =
                        jdbc.query(
                                "SELECT status FROM runtime_tool_proposal_inbox WHERE proposal_id=?",
                                resultSet -> resultSet.next() ? resultSet.getString(1) : null,
                                proposalId);
                if ("completed".equals(status)) break;
                Thread.sleep(250);
            }
            assertEquals("completed", status, "Proposal inbox must be completed within 30 seconds");
            assertTrue(
                    jdbc.queryForObject(
                                    "SELECT result_json::text FROM runtime_tool_proposal_inbox WHERE proposal_id=?",
                                    String.class,
                                    proposalId)
                            .contains("SQL_EXECUTION_FAILED"));
            assertEquals(
                    1,
                    jdbc.queryForObject(
                            "SELECT COUNT(*) FROM runtime_tool_proposal_inbox WHERE proposal_id=?",
                            Integer.class,
                            proposalId));
            assertEquals(
                    1,
                    jdbc.queryForObject(
                            "SELECT COUNT(*) FROM sql_query_audits WHERE trace_id=?",
                            Integer.class,
                            "proposal:" + proposalId));
        } finally {
            resultConsumer.close();
        }
    }
}
