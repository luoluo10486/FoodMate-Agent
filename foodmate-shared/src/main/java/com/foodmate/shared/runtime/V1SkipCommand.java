package com.foodmate.shared.runtime;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/** 版本化的 Java 到 Python 单个工具步骤跳过命令。 */
public record V1SkipCommand(
        @JsonProperty("schema_version") String schemaVersion,
        @JsonProperty("run_id") String runId,
        @JsonProperty("dispatch_id") String dispatchId,
        int attempt,
        @JsonProperty("skip_id") String skipId,
        @JsonProperty("proposal_id") String proposalId,
        @JsonProperty("invocation_id") String invocationId,
        @JsonProperty("tool_name") String toolName,
        @JsonProperty("request_id") String requestId,
        @JsonProperty("trace_id") String traceId,
        @JsonProperty("request_hash") String requestHash,
        @JsonProperty("proposal_request_hash") String proposalRequestHash,
        @JsonProperty("deadline_at") Instant deadlineAt,
        String reason,
        @JsonProperty("requested_at") Instant requestedAt) {
    public V1SkipCommand {
        if (!"v1".equals(schemaVersion))
            throw new IllegalArgumentException("schemaVersion must be v1");
        require(runId, "runId");
        require(dispatchId, "dispatchId");
        require(skipId, "skipId");
        require(proposalId, "proposalId");
        require(invocationId, "invocationId");
        require(requestId, "requestId");
        require(traceId, "traceId");
        require(requestHash, "requestHash");
        require(proposalRequestHash, "proposalRequestHash");
        if (attempt < 1 || reason == null || reason.isBlank() || reason.length() > 256)
            throw new IllegalArgumentException("invalid skip command");
        if (deadlineAt == null || requestedAt == null)
            throw new IllegalArgumentException("skip timestamps must not be null");
    }

    private static void require(String value, String name) {
        if (value == null || value.isBlank() || value.length() > 128)
            throw new IllegalArgumentException(name + " must not be blank");
    }
}
