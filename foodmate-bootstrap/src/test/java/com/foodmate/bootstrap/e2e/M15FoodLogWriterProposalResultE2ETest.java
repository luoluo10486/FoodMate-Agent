package com.foodmate.bootstrap.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.foodmate.application.account.service.UserAccountService;
import com.foodmate.application.food.service.ApprovalService;
import com.foodmate.application.runtime.messaging.MqConsumeDecision;
import com.foodmate.application.runtime.service.AgentRunCommandService;
import com.foodmate.infrastructure.messaging.rocketmq.RocketMqConsumerContainer;
import com.foodmate.infrastructure.messaging.rocketmq.RocketMqSettings;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
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

/** M1-5：food_log_writer 的真实 PostgreSQL + RocketMQ Proposal/Result 回归。 */
@SpringBootTest(
        properties = {
            "foodmate.runtime.transport=rocketmq",
            "foodmate.runtime.dispatch-poll-ms=3600000",
            "foodmate.runtime.dlq-reconcile-ms=3600000"
        })
@ActiveProfiles("local")
@EnabledIfSystemProperty(named = "foodmate.local-mq-e2e", matches = "true")
class M15FoodLogWriterProposalResultE2ETest {
    @Autowired UserAccountService accounts;
    @Autowired AgentRunCommandService runs;
    @Autowired ApprovalService approvals;
    @Autowired DefaultMQProducer producer;
    @Autowired RocketMqSettings settings;
    @Autowired JdbcTemplate jdbc;

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void writerProposalCreatesMatchedFoodLogAndReplayDoesNotDuplicate() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String username = "m15writer_" + suffix.substring(0, 16);
        long userId =
                accounts.register(username, username + "@example.com", "password123", "M1-5 Writer")
                        .userId();
        long sessionId = accounts.createSession(userId, "m1-5 writer", "agent").sessionId();
        long runId =
                runs.createUserMessageRun(userId, sessionId, "记录午餐", "trace-m15-writer")
                        .agentRunId();

        ObjectNode input = mapper.createObjectNode();
        input.put("meal_time", Instant.now().toString());
        input.put("meal_type", "lunch");
        input.put("notes", "M1-5 RocketMQ writer E2E");
        input.putArray("items").addObject().put("name", "rice").put("amount", 100).put("unit", "g");

        String approvalKey = "m15-writer-" + suffix;
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

        String proposalId = "m15-writer-proposal-" + suffix;
        String requestHash = "sha256:m15-writer-" + suffix;
        String invocationId = "m15-writer-invocation-" + suffix;
        String body =
                mapper.writeValueAsString(
                        Map.of(
                                "schema_version",
                                "v1",
                                "proposal_id",
                                proposalId,
                                "request_hash",
                                requestHash,
                                "run_id",
                                Long.toString(runId),
                                "proposal_type",
                                "tool",
                                "requires_confirmation",
                                true,
                                "tool_name",
                                "food_log_writer",
                                "confirmation_ref",
                                Long.toString(proposal.approvalRequestId()),
                                "input",
                                input,
                                "payload",
                                Map.of(
                                        "invocation_id",
                                        invocationId,
                                        "idempotency_key",
                                        approvalKey)));

        CountDownLatch firstResultReceived = new CountDownLatch(1);
        CountDownLatch resultsReceived = new CountDownLatch(2);
        CopyOnWriteArrayList<String> resultBodies = new CopyOnWriteArrayList<>();
        RocketMqConsumerContainer resultConsumer =
                RocketMqConsumerContainer.concurrent(
                        settings.nameServer(),
                        // Compose pre-creates this Python result group; the test filters by
                        // proposal_id.
                        "foodmate-python-agent-result-v1",
                        settings.resultTopic(),
                        1,
                        (result, context) -> {
                            try {
                                JsonNode node = mapper.readTree(result);
                                if (proposalId.equals(node.path("proposal_id").asText())) {
                                    resultBodies.add(result);
                                    firstResultReceived.countDown();
                                    resultsReceived.countDown();
                                }
                            } catch (Exception exception) {
                                return MqConsumeDecision.REJECT;
                            }
                            return MqConsumeDecision.ACK;
                        });
        resultConsumer.start();
        try {
            Thread.sleep(2500);
            publishProposal(runId, proposalId, requestHash, body);
            assertTrue(
                    firstResultReceived.await(30, TimeUnit.SECONDS),
                    "第一次 Proposal Result 必须在 30 秒内返回");
            publishProposal(runId, proposalId, requestHash, body);
            assertTrue(
                    resultsReceived.await(30, TimeUnit.SECONDS), "重放 Proposal Result 必须在 30 秒内返回");

            JsonNode firstResult = mapper.readTree(resultBodies.getFirst());
            assertEquals("success", firstResult.path("status").asText());
            String foodLogId = firstResult.path("rows").get(0).path("food_log_id").asText();
            assertNotNull(foodLogId);
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
                            "SELECT COUNT(*) FROM food_log_items WHERE food_log_id=? AND nutrition_status='matched' AND nutrition_food_id=510001",
                            Integer.class,
                            Long.parseLong(foodLogId)));
            assertEquals(
                    1,
                    jdbc.queryForObject(
                            "SELECT COUNT(*) FROM approval_requests WHERE approval_request_id=? AND status='executed' AND resource_id=?",
                            Integer.class,
                            proposal.approvalRequestId(),
                            Long.parseLong(foodLogId)));
            assertEquals(
                    1,
                    jdbc.queryForObject(
                            "SELECT COUNT(*) FROM runtime_tool_proposal_inbox WHERE proposal_id=? AND status='completed'",
                            Integer.class,
                            proposalId));
            assertEquals(
                    1,
                    jdbc.queryForObject(
                            "SELECT COUNT(*) FROM food_logs WHERE agent_run_id=?",
                            Integer.class,
                            runId));
        } finally {
            resultConsumer.close();
        }
    }

    private void publishProposal(long runId, String proposalId, String requestHash, String body)
            throws Exception {
        Message message =
                new Message(settings.proposalTopic(), body.getBytes(StandardCharsets.UTF_8));
        message.setKeys(Long.toString(runId));
        message.putUserProperty("foodmate_message_type", "ToolProposal");
        message.putUserProperty("foodmate_proposal_id", proposalId);
        message.putUserProperty("foodmate_request_hash", requestHash);
        assertNotNull(producer.send(message));
    }
}
