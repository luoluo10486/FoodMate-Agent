package com.foodmate.bootstrap.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.foodmate.application.runtime.messaging.MqConsumeDecision;
import com.foodmate.infrastructure.messaging.rocketmq.RocketMqConsumerContainer;
import com.foodmate.infrastructure.messaging.rocketmq.RocketMqSettings;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

/** M1-5: real PostgreSQL and RocketMQ regression for the food_log_writer gateway. */
@SpringBootTest(
        properties = {
            "foodmate.runtime.transport=rocketmq",
            "foodmate.runtime.dispatch-poll-ms=3600000",
            "foodmate.runtime.dlq-reconcile-ms=3600000"
        })
@ActiveProfiles("local")
@EnabledIfSystemProperty(named = "foodmate.local-mq-e2e", matches = "true")
class M15FoodLogWriterProposalResultE2ETest extends M15FoodLogWriterE2ETestSupport {
    @Autowired DefaultMQProducer producer;
    @Autowired RocketMqSettings settings;
    @Autowired JdbcTemplate jdbc;

    @Test
    void writerProposalCreatesMatchedFoodLogAndReplayDoesNotDuplicate() throws Exception {
        Fixture fixture = fixture("mq-create");
        var input = createInput("M1-5 RocketMQ writer E2E");
        String approvalKey = "m15-mq-create-" + fixture.suffix();
        var proposal = propose(fixture, "create", null, input, approvalKey, true);
        WriterRequest request = writerRequest(fixture, proposal, input, approvalKey, "mq-create");

        List<TransportResult> results = submit(request, 2);

        assertResult(results.getFirst(), "success", null);
        assertSameResult(results.getFirst(), results.get(1));
        String foodLogId = results.getFirst().rows().get(0).path("food_log_id").asText();
        assertNotNull(foodLogId);
        assertTrue(!foodLogId.isBlank());
        assertEquals(
                1,
                count(
                        "SELECT COUNT(*) FROM food_logs WHERE food_log_id=?",
                        Long.parseLong(foodLogId)));
        assertEquals(
                1,
                count(
                        "SELECT COUNT(*) FROM food_log_items WHERE food_log_id=? AND nutrition_status='matched' AND nutrition_food_id=510001",
                        Long.parseLong(foodLogId)));
        assertEquals(
                1,
                count(
                        "SELECT COUNT(*) FROM approval_requests WHERE approval_request_id=? AND status='executed' AND resource_id=?",
                        proposal.approvalRequestId(),
                        Long.parseLong(foodLogId)));
    }

    @Test
    void mqRejectedProposalDoesNotWrite() throws Exception {
        runRejected(this::submit);
    }

    @Test
    void mqFailedProposalRollsBackAndWritesFailureAudit() throws Exception {
        runFailed(this::submit);
    }

    @Test
    void mqSupersededProposalCannotExecute() throws Exception {
        runSuperseded(this::submit);
    }

    @Test
    void mqWriterUpdatesFoodLog() throws Exception {
        runUpdate(this::submit);
    }

    @Test
    void mqWriterDeletesFoodLog() throws Exception {
        runDelete(this::submit);
    }

    @Test
    void mqWriterRestoresFoodLog() throws Exception {
        runRestore(this::submit);
    }

    @Test
    void mqWriterRejectsStaleRevision() throws Exception {
        runRevisionConflict(this::submit);
    }

    @Test
    void mqSuccessfulProposalReplayIsIdempotent() throws Exception {
        runIdempotentReplay(this::submit);
    }

    @Test
    void mqWriterUsesReviewedFoodPortionConversion() throws Exception {
        runUnitConversion(this::submit);
    }

    @Test
    void mqWriterKeepsUnsupportedFoodPortionPending() throws Exception {
        runUnitConversionPending(this::submit);
    }

    private List<TransportResult> submit(WriterRequest request, int deliveries) throws Exception {
        String body =
                mapper.writeValueAsString(
                        Map.of(
                                "schema_version",
                                "v1",
                                "proposal_id",
                                request.proposalId(),
                                "request_hash",
                                request.requestHash(),
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
                                        request.idempotencyKey())));
        CountDownLatch resultsReceived = new CountDownLatch(deliveries);
        CopyOnWriteArrayList<JsonNode> resultNodes = new CopyOnWriteArrayList<>();
        RocketMqConsumerContainer resultConsumer =
                RocketMqConsumerContainer.concurrent(
                        settings.nameServer(),
                        "foodmate-python-agent-result-v1",
                        settings.resultTopic(),
                        1,
                        (result, context) -> {
                            try {
                                JsonNode node = mapper.readTree(result);
                                if (request.proposalId()
                                        .equals(node.path("proposal_id").asText())) {
                                    resultNodes.add(node);
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
            for (int i = 0; i < deliveries; i++) publishProposal(request, body);
            assertTrue(
                    resultsReceived.await(30, TimeUnit.SECONDS),
                    "all Proposal Results must arrive within 30 seconds");
            awaitInboxCompleted(request.proposalId());
            assertEquals(
                    1,
                    count(
                            "SELECT COUNT(*) FROM runtime_tool_proposal_inbox WHERE proposal_id=? AND status='completed'",
                            request.proposalId()));
            List<TransportResult> results = new ArrayList<>();
            for (JsonNode node : resultNodes) {
                results.add(
                        new TransportResult(
                                node.path("status").asText(), errorCode(node), node.path("rows")));
            }
            return results;
        } finally {
            resultConsumer.close();
        }
    }

    private void publishProposal(WriterRequest request, String body) throws Exception {
        Message message =
                new Message(settings.proposalTopic(), body.getBytes(StandardCharsets.UTF_8));
        message.setKeys(Long.toString(request.runId()));
        message.putUserProperty("foodmate_message_type", "ToolProposal");
        message.putUserProperty("foodmate_proposal_id", request.proposalId());
        message.putUserProperty("foodmate_request_hash", request.requestHash());
        assertNotNull(producer.send(message));
    }

    private void awaitInboxCompleted(String proposalId) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (System.nanoTime() < deadline) {
            String status =
                    jdbc.query(
                            "SELECT status FROM runtime_tool_proposal_inbox WHERE proposal_id=?",
                            resultSet -> resultSet.next() ? resultSet.getString(1) : null,
                            proposalId);
            if ("completed".equals(status)) return;
            Thread.sleep(100);
        }
        assertEquals(
                "completed",
                jdbc.query(
                        "SELECT status FROM runtime_tool_proposal_inbox WHERE proposal_id=?",
                        resultSet -> resultSet.next() ? resultSet.getString(1) : null,
                        proposalId));
    }

    private static String errorCode(JsonNode result) {
        JsonNode value = result.get("error_code");
        return value == null || value.isNull() || value.asText().isBlank() ? null : value.asText();
    }
}
