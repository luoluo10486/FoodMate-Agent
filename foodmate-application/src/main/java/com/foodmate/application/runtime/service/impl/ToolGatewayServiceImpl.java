package com.foodmate.application.runtime.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.foodmate.application.food.service.ApprovalService;
import com.foodmate.application.runtime.port.out.ToolGatewayPort;
import com.foodmate.application.runtime.service.SqlQueryGuard;
import com.foodmate.application.runtime.service.SqlSchemaCatalogService;
import com.foodmate.application.runtime.service.ToolGatewayService;
import com.foodmate.application.runtime.service.ToolPolicy;
import com.foodmate.application.runtime.service.ToolRegistryService;
import com.foodmate.shared.error.BusinessException;
import com.foodmate.shared.error.ErrorCode;
import com.foodmate.shared.id.IdGenerator;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Java Tool Gateway：Python 只提交 Proposal，Java 负责权限、SQL Guard、执行和审计。 */
@Service
public class ToolGatewayServiceImpl implements ToolGatewayService {
    private static final int MAX_ID_LENGTH = 128;
    private static final int MAX_SQL_LENGTH = 8_192;
    private final ToolGatewayPort store;
    private final IdGenerator ids;
    private final ApprovalService approvals;
    private final ObjectMapper mapper;
    private final ToolRegistryService registry;
    private final SqlQueryGuard sqlGuard;
    private final SqlSchemaCatalogService catalogService;

    public ToolGatewayServiceImpl(ToolGatewayPort store, IdGenerator ids) {
        this(
                store,
                ids,
                (ApprovalService) null,
                new ObjectMapper().findAndRegisterModules(),
                null,
                null,
                null);
    }

    @Autowired
    public ToolGatewayServiceImpl(
            ToolGatewayPort store,
            IdGenerator ids,
            ObjectProvider<ApprovalService> approvals,
            ObjectMapper mapper,
            ToolRegistryService registry,
            SqlQueryGuard sqlGuard,
            SqlSchemaCatalogService catalogService) {
        this(store, ids, approvals.getIfAvailable(), mapper, registry, sqlGuard, catalogService);
    }

    public ToolGatewayServiceImpl(
            ToolGatewayPort store,
            IdGenerator ids,
            ApprovalService approvals,
            ObjectMapper mapper) {
        this(store, ids, approvals, mapper, null, null, null);
    }

    public ToolGatewayServiceImpl(
            ToolGatewayPort store,
            IdGenerator ids,
            ApprovalService approvals,
            ObjectMapper mapper,
            ToolRegistryService registry) {
        this(store, ids, approvals, mapper, registry, null, null);
    }

    public ToolGatewayServiceImpl(
            ToolGatewayPort store,
            IdGenerator ids,
            ApprovalService approvals,
            ObjectMapper mapper,
            ToolRegistryService registry,
            SqlQueryGuard sqlGuard,
            SqlSchemaCatalogService catalogService) {
        this.store = store;
        this.ids = ids;
        this.approvals = approvals;
        this.mapper = mapper.copy().findAndRegisterModules();
        this.registry = registry;
        this.sqlGuard = sqlGuard;
        this.catalogService = catalogService;
    }

    @Override
    public ProposalResult execute(ProposalCommand proposal) {
        if (proposal == null) return reject(null, "PROPOSAL_NOT_ALLOWED");
        String proposalId = text(proposal.proposalId());
        String runId = text(proposal.runId());
        String type = text(proposal.proposalType());
        ProposalPayload payload = proposal.payload();
        String statement = payload == null ? null : text(payload.statement());
        String invocationId = payload == null ? null : text(payload.invocationId());
        if (!"v1".equals(text(proposal.schemaVersion()))
                || proposalId == null
                || runId == null
                || invocationId == null
                || proposalId.length() > MAX_ID_LENGTH
                || runId.length() > MAX_ID_LENGTH
                || invocationId.length() > MAX_ID_LENGTH)
            return reject(proposalId, "PROPOSAL_NOT_ALLOWED");
        if (registry != null) {
            ToolRegistryService.ToolView tool;
            String toolName =
                    "sql_read".equals(type) ? "database_query" : text(proposal.toolName());
            try {
                // schema_version is the wire protocol version; registry versioning is independent.
                tool = registry.resolve(toolName, null);
            } catch (BusinessException exception) {
                return reject(proposalId, exception.errorCode().code());
            }
            if ("sql_read".equals(type) && !"database_query".equals(tool.name()))
                return reject(proposalId, "TOOL_NAME_NOT_ALLOWED");
            if ("tool".equals(type)) {
                String schemaError = ToolPolicy.validateInput(tool, proposal.input());
                if (schemaError != null) return reject(proposalId, schemaError);
                if (ToolPolicy.requiresConfirmation(tool)
                        && (text(proposal.confirmationRef()) == null
                                || payload == null
                                || text(payload.idempotencyKey()) == null))
                    return result(
                            proposalId,
                            runId,
                            invocationId,
                            "confirmation_required",
                            "TOOL_CONFIRMATION_REQUIRED",
                            null);
            }
            if ("tool".equals(type)
                    && !"food_log_writer".equals(tool.name())
                    && !"meal_plan.save_plan".equals(tool.name()))
                return reject(proposalId, "TOOL_EXECUTOR_UNAVAILABLE");
        }
        if (registry == null) {
            if ("food_log_writer".equals(proposal.toolName()) && !"tool".equals(type))
                return reject(proposalId, "PROPOSAL_NOT_ALLOWED");
            if ("tool".equals(type) && !"food_log_writer".equals(proposal.toolName()))
                return reject(proposalId, "TOOL_NAME_NOT_ALLOWED");
            if ("sql_read".equals(type)
                    && proposal.toolName() != null
                    && !"database_query".equals(proposal.toolName()))
                return reject(proposalId, "TOOL_NAME_NOT_ALLOWED");
        }
        if ("food_log_writer".equals(proposal.toolName()))
            return executeApprovalWrite(
                    proposal, proposalId, runId, invocationId, "food_log_writer");
        if ("meal_plan.save_plan".equals(proposal.toolName()))
            return executeApprovalWrite(
                    proposal, proposalId, runId, invocationId, "meal_plan.save_plan");
        if (!"sql_read".equals(type)) return reject(proposalId, "PROPOSAL_NOT_ALLOWED");
        return executeValidated(proposalId, runId, statement, invocationId);
    }

    private ProposalResult executeApprovalWrite(
            ProposalCommand proposal,
            String proposalId,
            String runId,
            String invocationId,
            String toolName) {
        if (approvals == null || proposal.input() == null)
            return reject(proposalId, "TOOL_NOT_CONFIGURED");
        String confirmationRef = text(proposal.confirmationRef());
        String idempotencyKey =
                proposal.payload() == null ? null : text(proposal.payload().idempotencyKey());
        if (confirmationRef == null || idempotencyKey == null)
            return result(
                    proposalId,
                    runId,
                    invocationId,
                    "confirmation_required",
                    "TOOL_CONFIRMATION_REQUIRED",
                    null);
        long approvalId;
        long numericRunId;
        try {
            approvalId = Long.parseLong(confirmationRef);
            numericRunId = Long.parseLong(runId);
        } catch (NumberFormatException exception) {
            return reject(proposalId, "TOOL_CONFIRMATION_INVALID");
        }
        ToolGatewayPort.RunContext context = store.runContext(numericRunId);
        if (context == null) return reject(proposalId, "RUN_NOT_FOUND");
        long started = System.nanoTime();
        try {
            ApprovalService.ExecuteView execution =
                    approvals.executeForAgent(
                            context.userId(),
                            numericRunId,
                            approvalId,
                            idempotencyKey,
                            proposal.input());
            if (!"executed".equals(execution.status()))
                return result(
                        proposalId,
                        runId,
                        invocationId,
                        execution.status(),
                        "TOOL_EXECUTION_FAILED",
                        null);
            long resourceId = execution.resourceId();
            ObjectNode row = mapper.createObjectNode();
            row.put(
                    "meal_plan.save_plan".equals(toolName) ? "meal_plan_id" : "food_log_id",
                    Long.toString(resourceId));
            row.put("status", "saved");
            List<JsonNode> rows = List.of(row);
            store.audit(
                    new ToolGatewayPort.Audit(
                            ids.nextId(),
                            numericRunId,
                            toolName,
                            "executed",
                            1,
                            null,
                            (System.nanoTime() - started) / 1_000_000,
                            "proposal:" + proposalId));
            return result(proposalId, runId, invocationId, "success", null, rows);
        } catch (BusinessException exception) {
            String toolErrorCode = exception.details().path("tool_error_code").asText(null);
            String status;
            String errorCode;
            if ("TOOL_CONFIRMATION_REQUIRED".equals(toolErrorCode)
                    || "TOOL_CONFIRMATION_EXPIRED".equals(toolErrorCode)) {
                status = "confirmation_required";
                errorCode = toolErrorCode;
            } else if ("TOOL_CONFIRMATION_REJECTED".equals(toolErrorCode)) {
                status = "rejected";
                errorCode = toolErrorCode;
            } else if ("TOOL_CONFIRMATION_SUPERSEDED".equals(toolErrorCode)) {
                status = "superseded";
                errorCode = toolErrorCode;
            } else if ("TOOL_EXECUTION_FAILED".equals(toolErrorCode)) {
                status = "failed";
                errorCode = toolErrorCode;
            } else if ("TOOL_IDEMPOTENCY_CONFLICT".equals(toolErrorCode)) {
                status = "failed";
                errorCode = toolErrorCode;
            } else if (exception.errorCode() == ErrorCode.FORBIDDEN) {
                status = "denied";
                errorCode = "TOOL_POLICY_DENIED";
            } else if (exception.errorCode() == ErrorCode.CONFLICT) {
                status = "failed";
                errorCode = "TOOL_FAILED";
            } else {
                status = "denied";
                errorCode = "TOOL_POLICY_DENIED";
            }
            return result(proposalId, runId, invocationId, status, errorCode, null);
        } catch (RuntimeException exception) {
            return result(proposalId, runId, invocationId, "failed", "TOOL_EXECUTION_FAILED", null);
        }
    }

    private ProposalResult result(
            String proposalId,
            String runId,
            String invocationId,
            String status,
            String errorCode,
            List<JsonNode> rows) {
        return new ProposalResult(
                proposalId, runId, status, errorCode, rows == null ? List.of() : rows);
    }

    /** 执行最小 sql_read Proposal；无数据库时明确返回不可用，不回退到进程内伪造数据。 */
    private ProposalResult executeValidated(
            String proposalId, String runId, String statement, String invocationId) {
        if (statement == null || statement.length() > MAX_SQL_LENGTH)
            return reject(proposalId, "SQL_PROPOSAL_NOT_READ_ONLY");
        if (sqlGuard == null
                && !statement.trim().toLowerCase(java.util.Locale.ROOT).startsWith("select"))
            return reject(proposalId, "SQL_PROPOSAL_NOT_READ_ONLY");
        long numericRunId;
        try {
            numericRunId = Long.parseLong(runId);
        } catch (NumberFormatException exception) {
            return reject(proposalId, "RUN_ID_INVALID");
        }
        ToolGatewayPort.RunContext context = null;
        SqlQueryGuard.GuardedQuery guarded = null;
        if (sqlGuard != null && catalogService != null) {
            context = store.runContext(numericRunId);
            if (context == null) return reject(proposalId, "RUN_NOT_FOUND");
            try {
                guarded =
                        sqlGuard.guard(
                                statement,
                                catalogService.current(context.datasourceId()),
                                context.userId());
            } catch (BusinessException exception) {
                return reject(proposalId, exception.errorCode().code());
            }
        } else if (!store.runExists(numericRunId)) {
            return reject(proposalId, "RUN_NOT_FOUND");
        }
        long started = System.nanoTime();
        List<JsonNode> rows;
        try {
            rows =
                    guarded == null
                            ? store.executeRead(statement)
                            : store.executeRead(
                                    guarded.statement(), guarded.parameters(), guarded.timeoutMs());
            if (rows.size() > 500) rows = rows.subList(0, 500);
            store.audit(
                    new ToolGatewayPort.Audit(
                            ids.nextId(),
                            numericRunId,
                            guarded == null ? statement : guarded.statement(),
                            "executed",
                            rows.size(),
                            null,
                            (System.nanoTime() - started) / 1_000_000,
                            "proposal:" + proposalId));
            return new ProposalResult(proposalId, runId, "succeeded", null, rows);
        } catch (RuntimeException error) {
            store.audit(
                    new ToolGatewayPort.Audit(
                            ids.nextId(),
                            numericRunId,
                            statement,
                            "rejected",
                            null,
                            truncateReason(error.getMessage()),
                            (System.nanoTime() - started) / 1_000_000,
                            "proposal:" + proposalId));
            return new ProposalResult(
                    proposalId, runId, "failed", "SQL_EXECUTION_FAILED", List.of());
        }
    }

    private ProposalResult reject(String proposalId, String code) {
        return new ProposalResult(proposalId, null, "rejected", code, List.of());
    }

    /** 审计表的 reject_reason 是 varchar(255)，避免数据库异常文本反过来遮蔽原始失败结果。 */
    private static String truncateReason(String value) {
        if (value == null || value.isBlank()) return "tool execution failed";
        return value.substring(0, Math.min(255, value.length()));
    }

    private static String text(Object value) {
        if (value == null || value.toString().isBlank() || value.toString().length() > 10000)
            return null;
        return value.toString();
    }
}
