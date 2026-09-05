package com.foodmate.application.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.foodmate.application.common.service.OperationAuditService;
import com.foodmate.application.food.service.ApprovalService;
import com.foodmate.application.runtime.port.out.ToolGatewayPort;
import com.foodmate.application.runtime.service.ToolGatewayService;
import com.foodmate.application.runtime.service.impl.ToolGatewayServiceImpl;
import com.foodmate.shared.error.BusinessException;
import com.foodmate.shared.error.ErrorCode;
import com.foodmate.shared.id.IdGenerator;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class ToolGatewayServiceTest {
    private final ToolGatewayPort store = mock(ToolGatewayPort.class);
    private final ToolGatewayService gateway =
            new ToolGatewayServiceImpl(store, (IdGenerator) () -> 99L);

    @Test
    void rejectsWriteSqlBeforeDatabaseExecution() {
        var result = gateway.execute(proposal("UPDATE agent_runs SET status='completed'"));
        assertEquals("rejected", result.status());
        assertEquals("SQL_PROPOSAL_NOT_READ_ONLY", result.errorCode());
        verifyNoInteractions(store);
    }

    @Test
    void rejectsMalformedRunId() {
        var result =
                gateway.execute(
                        new ToolGatewayService.ProposalCommand(
                                "proposal-1",
                                "not-a-number",
                                "sql_read",
                                "v1",
                                new ToolGatewayService.ProposalPayload(
                                        "SELECT 1", "invocation-1")));
        assertEquals("RUN_ID_INVALID", result.errorCode());
        verifyNoInteractions(store);
    }

    @Test
    void rejectsProposalWithoutInvocationIdBeforeDatabaseExecution() {
        var result =
                gateway.execute(
                        new ToolGatewayService.ProposalCommand(
                                "proposal-1",
                                "42",
                                "sql_read",
                                "v1",
                                new ToolGatewayService.ProposalPayload("SELECT 1", null)));
        assertEquals("PROPOSAL_NOT_ALLOWED", result.errorCode());
        verifyNoInteractions(store);
    }

    @Test
    void rejectsUnknownSqlToolNameBeforeDatabaseExecution() {
        var result =
                gateway.execute(
                        new ToolGatewayService.ProposalCommand(
                                "proposal-1",
                                "42",
                                "sql_read",
                                "v1",
                                "unknown_tool",
                                null,
                                null,
                                new ToolGatewayService.ProposalPayload(
                                        "SELECT 1", "invocation-1")));

        assertEquals("rejected", result.status());
        assertEquals("TOOL_NAME_NOT_ALLOWED", result.errorCode());
        verifyNoInteractions(store);
    }

    @Test
    void executesReadAndAuditsIt() {
        when(store.runExists(42L)).thenReturn(true);
        when(store.executeRead("SELECT 1"))
                .thenReturn(List.of(JsonNodeFactory.instance.objectNode().put("value", 1)));
        var result = gateway.execute(proposal("SELECT 1"));
        assertEquals("succeeded", result.status());
        assertEquals(1, result.rows().size());
        verify(store).audit(any(ToolGatewayPort.Audit.class));
        ArgumentCaptor<ToolGatewayPort.ToolCall> toolCall =
                ArgumentCaptor.forClass(ToolGatewayPort.ToolCall.class);
        verify(store).recordToolCall(toolCall.capture());
        assertEquals("database_query", toolCall.getValue().toolName());
        assertEquals("success", toolCall.getValue().status());
        assertFalse(toolCall.getValue().inputJson().contains("SELECT 1"));
    }

    @Test
    void executesBoundedTimeParserWithoutModelOrDatabaseSql() {
        when(store.runExists(42L)).thenReturn(true);
        var input = JsonNodeFactory.instance.objectNode();
        input.put("question", "分析最近7天蛋白质摄入");
        input.put("timezone", "Asia/Shanghai");

        var result =
                gateway.execute(
                        new ToolGatewayService.ProposalCommand(
                                "proposal-time",
                                "42",
                                "tool",
                                "v1",
                                "time_parser",
                                null,
                                input,
                                new ToolGatewayService.ProposalPayload("", "inv-time", "key")));

        assertEquals("succeeded", result.status());
        assertEquals("time_parser", result.toolName());
        assertEquals("7", result.rows().getFirst().path("days").asText());
        verify(store).audit(any(ToolGatewayPort.Audit.class));
    }

    @Test
    void rejectsUnsupportedTimeExpressionWithoutExecutingSql() {
        when(store.runExists(42L)).thenReturn(true);
        var input = JsonNodeFactory.instance.objectNode().put("question", "分析我的摄入");

        var result =
                gateway.execute(
                        new ToolGatewayService.ProposalCommand(
                                "proposal-time-unsupported",
                                "42",
                                "tool",
                                "v1",
                                "time_parser",
                                null,
                                input,
                                new ToolGatewayService.ProposalPayload(
                                        "", "inv-time-unsupported", "key")));

        assertEquals("failed", result.status());
        assertEquals("TIME_RANGE_UNSUPPORTED", result.errorCode());
        verify(store, org.mockito.Mockito.never()).executeRead(anyString());
    }

    @Test
    void writerRequiresConfirmationAndDoesNotWriteWithoutIt() {
        ApprovalService approvals = Mockito.mock(ApprovalService.class);
        ToolGatewayService service =
                new ToolGatewayServiceImpl(
                        store,
                        () -> 99L,
                        approvals,
                        new com.fasterxml.jackson.databind.ObjectMapper());
        var input = JsonNodeFactory.instance.objectNode().put("meal_time", "2026-08-13T04:00:00Z");
        input.put("meal_type", "lunch");
        input.putArray("items").addObject().put("name", "rice").put("amount", 100).put("unit", "g");
        var result =
                service.execute(
                        new ToolGatewayService.ProposalCommand(
                                "proposal-writer",
                                "42",
                                "tool",
                                "v1",
                                "food_log_writer",
                                null,
                                input,
                                new ToolGatewayService.ProposalPayload(
                                        "", "inv-writer", "key-writer")));
        assertEquals("confirmation_required", result.status());
        assertEquals("TOOL_CONFIRMATION_REQUIRED", result.errorCode());
        verifyNoInteractions(approvals);
    }

    @Test
    void agentWriterCreatesPendingApprovalFromAnUnconfirmedProposal() {
        ApprovalService approvals = Mockito.mock(ApprovalService.class);
        when(store.runContext(42L)).thenReturn(new ToolGatewayPort.RunContext(7L, 8L));
        when(approvals.propose(eq(7L), any()))
                .thenReturn(
                        new ApprovalService.ProposalView(
                                100L,
                                "create",
                                "food_log",
                                null,
                                "digest",
                                "pending",
                                Instant.parse("2026-09-04T05:00:00Z"),
                                null,
                                null));
        ToolGatewayService service =
                new ToolGatewayServiceImpl(
                        store,
                        () -> 99L,
                        approvals,
                        new com.fasterxml.jackson.databind.ObjectMapper(),
                        null,
                        null,
                        null,
                        Mockito.mock(OperationAuditService.class));

        var result = service.execute(writerProposal(null));

        assertEquals("confirmation_required", result.status());
        assertEquals("100", result.confirmationRef());
        assertEquals("100", result.rows().getFirst().path("approval_request_id").asText());
        verify(approvals).propose(eq(7L), any());
    }

    @Test
    void agentMealPlanCreatesMealPlanApprovalFromAnUnconfirmedProposal() {
        ApprovalService approvals = Mockito.mock(ApprovalService.class);
        when(store.runContext(42L)).thenReturn(new ToolGatewayPort.RunContext(7L, 8L));
        when(approvals.propose(eq(7L), any()))
                .thenReturn(
                        new ApprovalService.ProposalView(
                                101L,
                                "save_plan",
                                "meal_plan",
                                null,
                                "digest",
                                "pending",
                                Instant.parse("2026-09-04T05:00:00Z"),
                                null,
                                null));
        ToolGatewayService service =
                new ToolGatewayServiceImpl(
                        store,
                        () -> 99L,
                        approvals,
                        new com.fasterxml.jackson.databind.ObjectMapper(),
                        null,
                        null,
                        null,
                        Mockito.mock(OperationAuditService.class));

        var result =
                service.execute(
                        new ToolGatewayService.ProposalCommand(
                                "proposal-plan",
                                "42",
                                "tool",
                                "v1",
                                "meal_plan.save_plan",
                                null,
                                mealPlanInput(),
                                new ToolGatewayService.ProposalPayload(
                                        "", "inv-plan", "plan-key")));

        assertEquals("confirmation_required", result.status());
        assertEquals("meal_plan.save_plan", result.toolName());
        assertEquals("101", result.confirmationRef());
        ArgumentCaptor<ApprovalService.ProposalCommand> command =
                ArgumentCaptor.forClass(ApprovalService.ProposalCommand.class);
        verify(approvals).propose(eq(7L), command.capture());
        assertEquals("save_plan", command.getValue().operation());
        assertEquals("meal_plan", command.getValue().resourceType());
        assertEquals(null, command.getValue().resourceId());
    }

    @Test
    void writerRejectsNonToolProposalType() {
        ApprovalService approvals = Mockito.mock(ApprovalService.class);
        ToolGatewayService service =
                new ToolGatewayServiceImpl(
                        store,
                        () -> 99L,
                        approvals,
                        new com.fasterxml.jackson.databind.ObjectMapper());

        var result =
                service.execute(
                        new ToolGatewayService.ProposalCommand(
                                "proposal-writer",
                                "42",
                                "sql_read",
                                "v1",
                                "food_log_writer",
                                "100",
                                writerInput(),
                                new ToolGatewayService.ProposalPayload(
                                        "", "inv-writer", "key-writer")));

        assertEquals("rejected", result.status());
        assertEquals("PROPOSAL_NOT_ALLOWED", result.errorCode());
        verifyNoInteractions(approvals);
    }

    @Test
    void writerExecutesThroughApprovalAndReturnsSavedRow() {
        ApprovalService approvals = Mockito.mock(ApprovalService.class);
        when(store.runContext(42L)).thenReturn(new ToolGatewayPort.RunContext(7L, 8L));
        when(approvals.executeForAgent(eq(7L), eq(42L), eq(100L), eq("key-writer"), any()))
                .thenReturn(new ApprovalService.ExecuteView(100L, "create", "executed", 501L));
        ToolGatewayService service =
                new ToolGatewayServiceImpl(
                        store,
                        () -> 99L,
                        approvals,
                        new com.fasterxml.jackson.databind.ObjectMapper());
        var input = JsonNodeFactory.instance.objectNode().put("meal_time", "2026-08-13T04:00:00Z");
        input.put("meal_type", "lunch");
        input.putArray("items").addObject().put("name", "rice").put("amount", 100).put("unit", "g");
        var result =
                service.execute(
                        new ToolGatewayService.ProposalCommand(
                                "proposal-writer",
                                "42",
                                "tool",
                                "v1",
                                "food_log_writer",
                                "100",
                                input,
                                new ToolGatewayService.ProposalPayload(
                                        "", "inv-writer", "key-writer")));
        assertEquals("success", result.status());
        assertEquals("501", result.rows().getFirst().path("food_log_id").asText());
        verify(approvals).executeForAgent(eq(7L), eq(42L), eq(100L), eq("key-writer"), any());
    }

    @Test
    void writerMapsUnconfirmedApprovalToConfirmationRequired() {
        ApprovalService approvals = Mockito.mock(ApprovalService.class);
        when(store.runContext(42L)).thenReturn(new ToolGatewayPort.RunContext(7L, 8L));
        when(approvals.executeForAgent(eq(7L), eq(42L), eq(100L), eq("key-writer"), any()))
                .thenThrow(
                        new BusinessException(
                                ErrorCode.CONFLICT,
                                "写操作尚未确认",
                                JsonNodeFactory.instance
                                        .objectNode()
                                        .put("tool_error_code", "TOOL_CONFIRMATION_REQUIRED")));
        ToolGatewayService service =
                new ToolGatewayServiceImpl(
                        store,
                        () -> 99L,
                        approvals,
                        new com.fasterxml.jackson.databind.ObjectMapper());

        var result =
                service.execute(
                        new ToolGatewayService.ProposalCommand(
                                "proposal-writer",
                                "42",
                                "tool",
                                "v1",
                                "food_log_writer",
                                "100",
                                writerInput(),
                                new ToolGatewayService.ProposalPayload(
                                        "", "inv-writer", "key-writer")));

        assertEquals("confirmation_required", result.status());
        assertEquals("TOOL_CONFIRMATION_REQUIRED", result.errorCode());
    }

    @Test
    void writerMapsIdempotencyConflictToFailed() {
        ApprovalService approvals = Mockito.mock(ApprovalService.class);
        when(store.runContext(42L)).thenReturn(new ToolGatewayPort.RunContext(7L, 8L));
        when(approvals.executeForAgent(eq(7L), eq(42L), eq(100L), eq("key-writer"), any()))
                .thenThrow(
                        new BusinessException(
                                ErrorCode.CONFLICT,
                                "工具幂等键与确认事实不一致",
                                JsonNodeFactory.instance
                                        .objectNode()
                                        .put("tool_error_code", "TOOL_IDEMPOTENCY_CONFLICT")));
        ToolGatewayService service =
                new ToolGatewayServiceImpl(
                        store,
                        () -> 99L,
                        approvals,
                        new com.fasterxml.jackson.databind.ObjectMapper());

        var result =
                service.execute(
                        new ToolGatewayService.ProposalCommand(
                                "proposal-writer",
                                "42",
                                "tool",
                                "v1",
                                "food_log_writer",
                                "100",
                                writerInput(),
                                new ToolGatewayService.ProposalPayload(
                                        "", "inv-writer", "key-writer")));

        assertEquals("failed", result.status());
        assertEquals("TOOL_IDEMPOTENCY_CONFLICT", result.errorCode());
    }

    @Test
    void writerPreservesRejectedAndSupersededConfirmationStates() {
        ApprovalService approvals = Mockito.mock(ApprovalService.class);
        when(store.runContext(42L)).thenReturn(new ToolGatewayPort.RunContext(7L, 8L));
        when(approvals.executeForAgent(eq(7L), eq(42L), eq(100L), eq("key-writer"), any()))
                .thenThrow(toolConflict("TOOL_CONFIRMATION_REJECTED"));
        ToolGatewayService service = writerService(approvals);

        var rejected = service.execute(writerProposal("100"));

        assertEquals("rejected", rejected.status());
        assertEquals("TOOL_CONFIRMATION_REJECTED", rejected.errorCode());

        when(approvals.executeForAgent(eq(7L), eq(42L), eq(100L), eq("key-writer"), any()))
                .thenThrow(toolConflict("TOOL_CONFIRMATION_SUPERSEDED"));
        var superseded = service.execute(writerProposal("100"));

        assertEquals("superseded", superseded.status());
        assertEquals("TOOL_CONFIRMATION_SUPERSEDED", superseded.errorCode());
    }

    @Test
    void writerMapsExpiredConfirmationToConfirmationRequired() {
        ApprovalService approvals = Mockito.mock(ApprovalService.class);
        when(store.runContext(42L)).thenReturn(new ToolGatewayPort.RunContext(7L, 8L));
        when(approvals.executeForAgent(eq(7L), eq(42L), eq(100L), eq("key-writer"), any()))
                .thenThrow(toolConflict("TOOL_CONFIRMATION_EXPIRED"));

        var result = writerService(approvals).execute(writerProposal("100"));

        assertEquals("confirmation_required", result.status());
        assertEquals("TOOL_CONFIRMATION_EXPIRED", result.errorCode());
    }

    private ToolGatewayService writerService(ApprovalService approvals) {
        return new ToolGatewayServiceImpl(
                store, () -> 99L, approvals, new com.fasterxml.jackson.databind.ObjectMapper());
    }

    private ToolGatewayService.ProposalCommand writerProposal(String confirmationRef) {
        return new ToolGatewayService.ProposalCommand(
                "proposal-writer",
                "42",
                "tool",
                "v1",
                "food_log_writer",
                confirmationRef,
                writerInput(),
                new ToolGatewayService.ProposalPayload("", "inv-writer", "key-writer"));
    }

    private BusinessException toolConflict(String code) {
        return new BusinessException(
                ErrorCode.CONFLICT,
                code,
                JsonNodeFactory.instance.objectNode().put("tool_error_code", code));
    }

    private static JsonNode writerInput() {
        var input = JsonNodeFactory.instance.objectNode().put("meal_time", "2026-08-13T04:00:00Z");
        input.put("meal_type", "lunch");
        input.putArray("items").addObject().put("name", "rice").put("amount", 100).put("unit", "g");
        return input;
    }

    private static JsonNode mealPlanInput() {
        var plan = JsonNodeFactory.instance.objectNode();
        plan.put("plan_name", "一日计划");
        plan.put("people", 1);
        plan.put("days", 1);
        plan.put("budget", 80);
        plan.putArray("allergens");
        plan.putArray("dislikes");
        var day = plan.putArray("days_plan").addObject();
        day.putObject("breakfast");
        day.putObject("lunch");
        day.putObject("dinner");
        return JsonNodeFactory.instance.objectNode().set("plan", plan);
    }

    private static ToolGatewayService.ProposalCommand proposal(String sql) {
        return new ToolGatewayService.ProposalCommand(
                "proposal-1",
                "42",
                "sql_read",
                "v1",
                new ToolGatewayService.ProposalPayload(sql, "invocation-1"));
    }
}
