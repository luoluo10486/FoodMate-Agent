package com.foodmate.bootstrap.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.foodmate.application.account.service.UserAccountService;
import com.foodmate.application.food.service.ApprovalService;
import com.foodmate.application.food.service.FoodLogService;
import com.foodmate.application.runtime.service.AgentRunCommandService;
import com.foodmate.shared.food.enums.MealType;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Shared fixtures and assertions for the two real M1-5 writer transports. */
abstract class M15FoodLogWriterE2ETestSupport {
    protected final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Autowired protected UserAccountService accounts;
    @Autowired protected AgentRunCommandService runs;
    @Autowired protected ApprovalService approvals;
    @Autowired protected FoodLogService foods;
    @Autowired protected JdbcTemplate jdbc;

    protected Fixture fixture(String label) {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String username = "m15" + label + "_" + suffix.substring(0, 16);
        long userId =
                accounts.register(
                                username, username + "@example.com", "password123", "M1-5 " + label)
                        .userId();
        long sessionId = accounts.createSession(userId, "m1-5 " + label, "agent").sessionId();
        long runId =
                runs.createUserMessageRun(userId, sessionId, "M1-5 " + label, "trace-m15-" + suffix)
                        .agentRunId();
        return new Fixture(userId, sessionId, runId, suffix);
    }

    protected FoodLogService.FoodLogView seedFoodLog(Fixture fixture, String key) {
        return foods.create(
                fixture.userId(),
                new FoodLogService.CreateCommand(
                        fixture.sessionId(),
                        fixture.runId(),
                        Instant.now().minusSeconds(60),
                        MealType.LUNCH,
                        "M1-5 base food log",
                        key,
                        "agent",
                        List.of(
                                new FoodLogService.ItemCommand(
                                        "rice", new BigDecimal("100"), "g"))));
    }

    protected ApprovalService.ProposalView propose(
            Fixture fixture,
            String operation,
            Long resourceId,
            JsonNode input,
            String key,
            boolean confirm) {
        ApprovalService.ProposalView proposal =
                approvals.propose(
                        fixture.userId(),
                        new ApprovalService.ProposalCommand(
                                fixture.sessionId(),
                                fixture.runId(),
                                operation,
                                "food_log",
                                resourceId,
                                input,
                                key,
                                300));
        if (confirm) approvals.confirm(fixture.userId(), proposal.approvalRequestId(), input);
        return proposal;
    }

    protected WriterRequest writerRequest(
            Fixture fixture,
            ApprovalService.ProposalView proposal,
            ObjectNode input,
            String key,
            String label) {
        return new WriterRequest(
                "m15-" + label + "-proposal-" + fixture.suffix().substring(0, 20),
                "sha256:m15-" + label + "-" + fixture.suffix(),
                fixture.runId(),
                proposal.approvalRequestId(),
                input,
                key,
                "m15-" + label + "-invocation-" + fixture.suffix());
    }

    protected ObjectNode createInput(String notes) {
        ObjectNode input = mapper.createObjectNode();
        input.put("meal_time", Instant.now().minusSeconds(30).toString());
        input.put("meal_type", "lunch");
        input.put("notes", notes);
        input.putArray("items").addObject().put("name", "rice").put("amount", 100).put("unit", "g");
        return input;
    }

    protected ObjectNode updateInput(long revision, String notes, int amount) {
        ObjectNode input = createInput(notes);
        input.put("revision", revision);
        ((ObjectNode) input.withArray("items").get(0)).put("amount", amount);
        return input;
    }

    protected ObjectNode revisionInput(long revision) {
        return mapper.createObjectNode().put("revision", revision);
    }

    protected void runRejected(ProposalSubmitter submitter) throws Exception {
        Fixture fixture = fixture("rejected");
        ObjectNode input = createInput("rejected writer");
        String key = "m15-rejected-" + fixture.suffix();
        ApprovalService.ProposalView proposal = propose(fixture, "create", null, input, key, false);
        ApprovalService.ProposalView rejected =
                approvals.reject(fixture.userId(), proposal.approvalRequestId(), input);

        List<TransportResult> results =
                submitter.submit(writerRequest(fixture, rejected, input, key, "rejected"), 1);

        assertResult(results.getFirst(), "rejected", "TOOL_CONFIRMATION_REJECTED");
        assertEquals(
                0,
                count(
                        "SELECT COUNT(*) FROM food_logs WHERE user_id=? AND agent_run_id=?",
                        fixture.userId(),
                        fixture.runId()));
        assertApprovalStatus(proposal.approvalRequestId(), "rejected");
        assertAudit(fixture.userId(), proposal.approvalRequestId(), "approval.reject", 1);
        assertAudit(fixture.userId(), proposal.approvalRequestId(), "approval.execute", 0);
    }

    protected void runFailed(ProposalSubmitter submitter) throws Exception {
        Fixture fixture = fixture("failed");
        FoodLogService.FoodLogView base =
                seedFoodLog(fixture, "m15-failed-base-" + fixture.suffix());
        ObjectNode staleInput = updateInput(1, "stale update must fail", 150);
        String key = "m15-failed-" + fixture.suffix();
        ApprovalService.ProposalView proposal =
                propose(fixture, "update", base.foodLogId(), staleInput, key, true);

        foods.update(
                fixture.userId(),
                base.foodLogId(),
                1,
                new FoodLogService.UpdateCommand(
                        Instant.now(),
                        MealType.LUNCH,
                        "external update wins",
                        "m15-failed-external-" + fixture.suffix(),
                        List.of(
                                new FoodLogService.ItemCommand(
                                        "rice", new BigDecimal("200"), "g"))));

        List<TransportResult> results =
                submitter.submit(writerRequest(fixture, proposal, staleInput, key, "failed"), 1);

        assertResult(results.getFirst(), "failed", "TOOL_FAILED");
        assertApprovalStatus(proposal.approvalRequestId(), "failed");
        assertFoodLog(base.foodLogId(), 2, false, "external update wins");
        assertAudit(fixture.userId(), proposal.approvalRequestId(), "approval.failed", 1);
        assertAudit(fixture.userId(), proposal.approvalRequestId(), "approval.execute", 0);
        assertFoodAudit(fixture.userId(), key, "food_log.update", 0);
    }

    protected void runSuperseded(ProposalSubmitter submitter) throws Exception {
        Fixture fixture = fixture("superseded");
        FoodLogService.FoodLogView base =
                seedFoodLog(fixture, "m15-superseded-base-" + fixture.suffix());
        ObjectNode oldInput = updateInput(1, "old proposal", 150);
        String oldKey = "m15-superseded-old-" + fixture.suffix();
        ApprovalService.ProposalView oldProposal =
                propose(fixture, "update", base.foodLogId(), oldInput, oldKey, true);
        ObjectNode newInput = updateInput(1, "new proposal", 175);
        String newKey = "m15-superseded-new-" + fixture.suffix();
        propose(fixture, "update", base.foodLogId(), newInput, newKey, false);

        List<TransportResult> results =
                submitter.submit(
                        writerRequest(fixture, oldProposal, oldInput, oldKey, "superseded"), 1);

        assertResult(results.getFirst(), "superseded", "TOOL_CONFIRMATION_SUPERSEDED");
        assertApprovalStatus(oldProposal.approvalRequestId(), "superseded");
        assertFoodLog(base.foodLogId(), 1, false, "M1-5 base food log");
        assertAudit(fixture.userId(), oldProposal.approvalRequestId(), "approval.execute", 0);
    }

    protected void runUpdate(ProposalSubmitter submitter) throws Exception {
        Fixture fixture = fixture("update");
        FoodLogService.FoodLogView base =
                seedFoodLog(fixture, "m15-update-base-" + fixture.suffix());
        ObjectNode input = updateInput(1, "updated by writer", 150);
        String key = "m15-update-" + fixture.suffix();
        ApprovalService.ProposalView proposal =
                propose(fixture, "update", base.foodLogId(), input, key, true);

        List<TransportResult> results =
                submitter.submit(writerRequest(fixture, proposal, input, key, "update"), 1);

        assertResult(results.getFirst(), "success", null);
        assertFoodLog(base.foodLogId(), 2, false, "updated by writer");
        assertMatchedRice(base.foodLogId(), 150);
        assertApprovalStatus(proposal.approvalRequestId(), "executed");
        assertEquals(
                base.foodLogId(),
                jdbc.queryForObject(
                        "SELECT resource_id FROM approval_requests WHERE approval_request_id=?",
                        Long.class,
                        proposal.approvalRequestId()));
        assertAudit(fixture.userId(), proposal.approvalRequestId(), "approval.execute", 1);
        assertFoodAudit(fixture.userId(), key, "food_log.update", 1);
    }

    protected void runDelete(ProposalSubmitter submitter) throws Exception {
        Fixture fixture = fixture("delete");
        FoodLogService.FoodLogView base =
                seedFoodLog(fixture, "m15-delete-base-" + fixture.suffix());
        ObjectNode input = revisionInput(1);
        String key = "m15-delete-" + fixture.suffix();
        ApprovalService.ProposalView proposal =
                propose(fixture, "delete", base.foodLogId(), input, key, true);

        List<TransportResult> results =
                submitter.submit(writerRequest(fixture, proposal, input, key, "delete"), 1);

        assertResult(results.getFirst(), "success", null);
        assertFoodLog(base.foodLogId(), 2, true, "M1-5 base food log");
        assertEquals(
                0,
                count(
                        "SELECT COUNT(*) FROM food_logs WHERE food_log_id=? AND is_deleted=FALSE",
                        base.foodLogId()));
        assertApprovalStatus(proposal.approvalRequestId(), "executed");
        assertAudit(fixture.userId(), proposal.approvalRequestId(), "approval.execute", 1);
        assertFoodAudit(fixture.userId(), key, "food_log.delete", 1);
    }

    protected void runRestore(ProposalSubmitter submitter) throws Exception {
        Fixture fixture = fixture("restore");
        FoodLogService.FoodLogView base =
                seedFoodLog(fixture, "m15-restore-base-" + fixture.suffix());
        foods.delete(
                fixture.userId(), base.foodLogId(), 1, "m15-restore-setup-" + fixture.suffix());
        ObjectNode input = revisionInput(2);
        String key = "m15-restore-" + fixture.suffix();
        ApprovalService.ProposalView proposal =
                propose(fixture, "restore", base.foodLogId(), input, key, true);

        List<TransportResult> results =
                submitter.submit(writerRequest(fixture, proposal, input, key, "restore"), 1);

        assertResult(results.getFirst(), "success", null);
        assertFoodLog(base.foodLogId(), 3, false, "M1-5 base food log");
        assertApprovalStatus(proposal.approvalRequestId(), "executed");
        assertAudit(fixture.userId(), proposal.approvalRequestId(), "approval.execute", 1);
        assertFoodAudit(fixture.userId(), key, "food_log.restore", 1);
    }

    protected void runRevisionConflict(ProposalSubmitter submitter) throws Exception {
        Fixture fixture = fixture("revision");
        FoodLogService.FoodLogView base =
                seedFoodLog(fixture, "m15-revision-base-" + fixture.suffix());
        foods.delete(
                fixture.userId(), base.foodLogId(), 1, "m15-revision-setup-" + fixture.suffix());
        ObjectNode input = revisionInput(1);
        String key = "m15-revision-" + fixture.suffix();
        ApprovalService.ProposalView proposal =
                propose(fixture, "restore", base.foodLogId(), input, key, true);

        List<TransportResult> results =
                submitter.submit(writerRequest(fixture, proposal, input, key, "revision"), 1);

        assertResult(results.getFirst(), "failed", "TOOL_FAILED");
        assertApprovalStatus(proposal.approvalRequestId(), "failed");
        assertFoodLog(base.foodLogId(), 2, true, "M1-5 base food log");
        assertAudit(fixture.userId(), proposal.approvalRequestId(), "approval.failed", 1);
        assertFoodAudit(fixture.userId(), key, "food_log.restore", 0);
    }

    protected void runIdempotentReplay(ProposalSubmitter submitter) throws Exception {
        Fixture fixture = fixture("idempotent");
        FoodLogService.FoodLogView base =
                seedFoodLog(fixture, "m15-idempotent-base-" + fixture.suffix());
        ObjectNode input = updateInput(1, "idempotent update", 125);
        String key = "m15-idempotent-" + fixture.suffix();
        ApprovalService.ProposalView proposal =
                propose(fixture, "update", base.foodLogId(), input, key, true);

        List<TransportResult> results =
                submitter.submit(writerRequest(fixture, proposal, input, key, "idempotent"), 2);

        assertResult(results.getFirst(), "success", null);
        assertSameResult(results.getFirst(), results.get(1));
        assertFoodLog(base.foodLogId(), 2, false, "idempotent update");
        assertMatchedRice(base.foodLogId(), 125);
        assertFoodAudit(fixture.userId(), key, "food_log.update", 1);
        assertAudit(fixture.userId(), proposal.approvalRequestId(), "approval.execute", 1);
    }

    protected void runUnitConversion(ProposalSubmitter submitter) throws Exception {
        Fixture fixture = fixture("unit-conversion");
        ObjectNode input = createUnitInput("rice", "杯", 1, "reviewed cup conversion");
        String key = "m15-unit-conversion-" + fixture.suffix();
        ApprovalService.ProposalView proposal = propose(fixture, "create", null, input, key, true);

        List<TransportResult> results =
                submitter.submit(
                        writerRequest(fixture, proposal, input, key, "unit-conversion"), 1);

        assertResult(results.getFirst(), "success", null);
        long foodLogId = results.getFirst().rows().get(0).path("food_log_id").asLong();
        assertEquals(
                1,
                count(
                        "SELECT COUNT(*) FROM food_log_items WHERE food_log_id=? AND nutrition_status='matched' AND nutrition_food_id=510001 AND normalized_amount=? AND normalized_unit='g' AND conversion_id=520001",
                        foodLogId,
                        new BigDecimal("186.000")));
        assertEquals(
                new BigDecimal("241.8000"),
                jdbc.queryForObject(
                        "SELECT calories_kcal FROM food_log_items WHERE food_log_id=? AND is_deleted=FALSE",
                        BigDecimal.class,
                        foodLogId));
        assertApprovalStatus(proposal.approvalRequestId(), "executed");
        assertFoodAudit(fixture.userId(), key, "food_log.create", 1);
    }

    protected void runUnitConversionPending(ProposalSubmitter submitter) throws Exception {
        Fixture fixture = fixture("unit-pending");
        ObjectNode input = createUnitInput("rice", "个", 1, "unsupported household unit");
        String key = "m15-unit-pending-" + fixture.suffix();
        ApprovalService.ProposalView proposal = propose(fixture, "create", null, input, key, true);

        List<TransportResult> results =
                submitter.submit(writerRequest(fixture, proposal, input, key, "unit-pending"), 1);

        assertResult(results.getFirst(), "success", null);
        long foodLogId = results.getFirst().rows().get(0).path("food_log_id").asLong();
        assertEquals(
                1,
                count(
                        "SELECT COUNT(*) FROM food_log_items WHERE food_log_id=? AND nutrition_status='pending' AND nutrition_food_id IS NULL AND normalized_amount IS NULL AND conversion_id IS NULL",
                        foodLogId));
        assertApprovalStatus(proposal.approvalRequestId(), "executed");
        assertFoodAudit(fixture.userId(), key, "food_log.create", 1);
    }

    protected void assertResult(TransportResult result, String status, String errorCode) {
        assertEquals(
                status,
                result.status(),
                () ->
                        "unexpected transport status, errorCode="
                                + result.errorCode()
                                + ", rows="
                                + result.rows());
        assertEquals(
                errorCode,
                result.errorCode(),
                () -> "unexpected transport error code, status=" + result.status());
    }

    protected void assertSameResult(TransportResult first, TransportResult second) {
        assertEquals(first.status(), second.status());
        assertEquals(first.errorCode(), second.errorCode());
        assertEquals(first.rows(), second.rows());
    }

    protected void assertApprovalStatus(long approvalId, String status) {
        assertEquals(
                status,
                jdbc.queryForObject(
                        "SELECT status FROM approval_requests WHERE approval_request_id=?",
                        String.class,
                        approvalId));
    }

    protected void assertFoodLog(long foodLogId, long revision, boolean deleted, String notes) {
        assertEquals(
                revision,
                jdbc.queryForObject(
                        "SELECT revision FROM food_logs WHERE food_log_id=?",
                        Long.class,
                        foodLogId));
        assertEquals(
                deleted,
                jdbc.queryForObject(
                        "SELECT is_deleted FROM food_logs WHERE food_log_id=?",
                        Boolean.class,
                        foodLogId));
        assertEquals(
                notes,
                jdbc.queryForObject(
                        "SELECT notes FROM food_logs WHERE food_log_id=?",
                        String.class,
                        foodLogId));
    }

    protected void assertMatchedRice(long foodLogId, int amount) {
        assertEquals(
                1,
                count(
                        "SELECT COUNT(*) FROM food_log_items WHERE food_log_id=? AND is_deleted=FALSE AND raw_name='rice' AND amount=? AND nutrition_status='matched' AND nutrition_food_id=510001",
                        foodLogId,
                        new BigDecimal(amount)));
    }

    protected void assertAudit(long userId, long approvalId, String action, int expected) {
        assertEquals(
                expected,
                count(
                        "SELECT COUNT(*) FROM operation_audits WHERE operator_id=? AND target_type='approval_request' AND target_id=? AND action=?",
                        userId,
                        Long.toString(approvalId),
                        action));
    }

    protected void assertFoodAudit(long userId, String key, String action, int expected) {
        String operation = action.substring("food_log.".length());
        String businessKey = "food_" + digest("food_log." + operation, key);
        assertEquals(
                expected,
                count(
                        "SELECT COUNT(*) FROM operation_audits WHERE operator_id=? AND target_type='food_log' AND idempotency_key=? AND action=?",
                        userId,
                        businessKey,
                        action));
    }

    private String digest(Object... values) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(mapper.writeValueAsBytes(List.of(values))));
        } catch (NoSuchAlgorithmException
                | com.fasterxml.jackson.core.JsonProcessingException exception) {
            throw new IllegalStateException(
                    "cannot calculate test business idempotency key", exception);
        }
    }

    protected int count(String sql, Object... arguments) {
        return jdbc.queryForObject(sql, Integer.class, arguments);
    }

    protected ObjectNode createUnitInput(String food, String unit, int amount, String notes) {
        ObjectNode input = createInput(notes);
        ObjectNode item = (ObjectNode) input.withArray("items").get(0);
        item.put("name", food);
        item.put("unit", unit);
        item.put("amount", amount);
        return input;
    }

    protected record Fixture(long userId, long sessionId, long runId, String suffix) {}

    protected record WriterRequest(
            String proposalId,
            String requestHash,
            long runId,
            long approvalRequestId,
            ObjectNode input,
            String idempotencyKey,
            String invocationId) {}

    protected record TransportResult(String status, String errorCode, JsonNode rows) {}

    @FunctionalInterface
    protected interface ProposalSubmitter {
        List<TransportResult> submit(WriterRequest request, int deliveries) throws Exception;
    }
}
