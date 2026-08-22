package com.foodmate.application.runtime.service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/** Validates and executes the application-facing tool proposal contract. */
public interface ToolGatewayService {
    ProposalResult execute(ProposalCommand proposal);

    record ProposalResult(
            String proposalId,
            String runId,
            String status,
            String errorCode,
            List<JsonNode> rows,
            @JsonProperty("sql_audit_id") String sqlAuditId) {
        public ProposalResult(
                String proposalId,
                String runId,
                String status,
                String errorCode,
                List<JsonNode> rows) {
            this(proposalId, runId, status, errorCode, rows, null);
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
