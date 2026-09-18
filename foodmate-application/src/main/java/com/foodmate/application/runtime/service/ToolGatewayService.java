package com.foodmate.application.runtime.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/** 校验并执行面向应用层的工具提案契约。 */
public interface ToolGatewayService {
    ProposalResult execute(ProposalCommand proposal);

    record ProposalResult(
            String proposalId,
            String runId,
            String status,
            String errorCode,
            List<JsonNode> rows,
            @JsonProperty("sql_audit_id") String sqlAuditId,
            @JsonProperty("tool_name") String toolName,
            @JsonProperty("confirmation_ref") String confirmationRef,
            @JsonProperty("skippable") boolean skippable) {
        public ProposalResult(
                String proposalId,
                String runId,
                String status,
                String errorCode,
                List<JsonNode> rows) {
            this(proposalId, runId, status, errorCode, rows, null, null, null, false);
        }

        public ProposalResult(
                String proposalId,
                String runId,
                String status,
                String errorCode,
                List<JsonNode> rows,
                String sqlAuditId) {
            this(proposalId, runId, status, errorCode, rows, sqlAuditId, null, null, false);
        }

        public ProposalResult(
                String proposalId,
                String runId,
                String status,
                String errorCode,
                List<JsonNode> rows,
                String sqlAuditId,
                String toolName) {
            this(proposalId, runId, status, errorCode, rows, sqlAuditId, toolName, null, false);
        }

        public ProposalResult(
                String proposalId,
                String runId,
                String status,
                String errorCode,
                List<JsonNode> rows,
                String sqlAuditId,
                String toolName,
                String confirmationRef) {
            this(
                    proposalId,
                    runId,
                    status,
                    errorCode,
                    rows,
                    sqlAuditId,
                    toolName,
                    confirmationRef,
                    false);
        }

        /** 返回同一工具结果并附加由 Java 注册表裁决的可跳过标记。 */
        public ProposalResult withSkippable(boolean value) {
            return new ProposalResult(
                    proposalId,
                    runId,
                    status,
                    errorCode,
                    rows,
                    sqlAuditId,
                    toolName,
                    confirmationRef,
                    value);
        }
    }

    record ProposalCommand(
            String proposalId,
            String runId,
            String proposalType,
            String schemaVersion,
            String toolName,
            String confirmationRef,
            JsonNode input,
            ProposalPayload payload) {
        public ProposalCommand(
                String proposalId,
                String runId,
                String proposalType,
                String schemaVersion,
                ProposalPayload payload) {
            this(proposalId, runId, proposalType, schemaVersion, null, null, null, payload);
        }
    }

    record ProposalPayload(String statement, String invocationId, String idempotencyKey) {
        public ProposalPayload(String statement, String invocationId) {
            this(statement, invocationId, null);
        }
    }
}
