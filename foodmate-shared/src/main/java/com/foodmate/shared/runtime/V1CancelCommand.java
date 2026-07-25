package com.foodmate.shared.runtime;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

public record V1CancelCommand(
        @JsonProperty("schema_version") String schemaVersion,
        @JsonProperty("run_id") String runId,
        @JsonProperty("dispatch_id") String dispatchId,
        int attempt,
        @JsonProperty("cancel_id") String cancelId,
        @JsonProperty("request_id") String requestId,
        @JsonProperty("trace_id") String traceId,
        @JsonProperty("request_hash") String requestHash,
        @JsonProperty("deadline_at") Instant deadlineAt,
        String reason,
        @JsonProperty("requested_at") Instant requestedAt) {
    public V1CancelCommand {
        if (!"v1".equals(schemaVersion)) throw new IllegalArgumentException("schemaVersion must be v1");
        if (runId == null || runId.isBlank() || dispatchId == null || dispatchId.isBlank() || cancelId == null || cancelId.isBlank()) {
            throw new IllegalArgumentException("runId, dispatchId and cancelId must not be blank");
        }
        if (attempt < 1 || reason == null || reason.isBlank()) throw new IllegalArgumentException("invalid cancel command");
    }
}
