package com.foodmate.shared.runtime;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;

/** V1 Java -> Python tool result message. */
public record V1ToolResult(
        @JsonProperty("schema_version") String schemaVersion,
        @JsonProperty("proposal_id") String proposalId,
        @JsonProperty("request_hash") String requestHash,
        @JsonProperty("run_id") String runId,
        @JsonProperty("invocation_id") String invocationId,
        String status,
        @JsonProperty("error_code") String errorCode,
        List<JsonNode> rows,
        @JsonProperty("sql_audit_id") String sqlAuditId,
        @JsonProperty("tool_name") String toolName) {
    public V1ToolResult(
            String schemaVersion,
            String proposalId,
            String requestHash,
            String runId,
            String invocationId,
            String status,
            String errorCode,
            List<JsonNode> rows) {
        this(
                schemaVersion,
                proposalId,
                requestHash,
                runId,
                invocationId,
                status,
                errorCode,
                rows,
                null,
                null);
    }

    public V1ToolResult(
            String schemaVersion,
            String proposalId,
            String requestHash,
            String runId,
            String invocationId,
            String status,
            String errorCode,
            List<JsonNode> rows,
            String sqlAuditId) {
        this(
                schemaVersion,
                proposalId,
                requestHash,
                runId,
                invocationId,
                status,
                errorCode,
                rows,
                sqlAuditId,
                null);
    }
}
