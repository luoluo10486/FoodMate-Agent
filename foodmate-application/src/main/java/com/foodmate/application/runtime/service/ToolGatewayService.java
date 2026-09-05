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
            @JsonProperty("confirmation_ref") String confirmationRef) {
        public ProposalResult(
                String proposalId,
                String runId,
                String status,
                String errorCode,
                List<JsonNode> rows) {
            this(proposalId, runId, status, errorCode, rows, null, null, null);
        }

        public ProposalResult(
                String proposalId,
                String runId,
                String status,
                String errorCode,
                List<JsonNode> rows,
                String sqlAuditId) {
            this(proposalId, runId, status, errorCode, rows, sqlAuditId, null, null);
        }

        public ProposalResult(
                String proposalId,
                String runId,
                String status,
                String errorCode,
                List<JsonNode> rows,
                String sqlAuditId,
                String toolName) {
            this(proposalId, runId, status, errorCode, rows, sqlAuditId, toolName, null);
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
