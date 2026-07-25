package com.foodmate.shared.runtime;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/** Immutable Java representation of the V1 Java -> Python RunCommand envelope. */
public record V1RunCommand(
        @JsonProperty("schema_version") String schemaVersion,
        @JsonProperty("run_id") String runId,
        @JsonProperty("dispatch_id") String dispatchId,
        int attempt,
        @JsonProperty("request_id") String requestId,
        @JsonProperty("trace_id") String traceId,
        @JsonProperty("request_hash") String requestHash,
        @JsonProperty("deadline_at") Instant deadlineAt,
        V1Message message,
        @JsonProperty("authorized_context") Map<String, Object> authorizedContext,
        @JsonProperty("runtime_options") Map<String, Object> runtimeOptions) {
    public V1RunCommand {
        require(schemaVersion, "schemaVersion"); require(runId, "runId"); require(dispatchId, "dispatchId");
        require(requestId, "requestId"); require(traceId, "traceId"); require(requestHash, "requestHash");
        Objects.requireNonNull(deadlineAt, "deadlineAt");
        Objects.requireNonNull(message, "message"); Objects.requireNonNull(authorizedContext, "authorizedContext");
        Objects.requireNonNull(runtimeOptions, "runtimeOptions");
        if (!"v1".equals(schemaVersion)) throw new IllegalArgumentException("schemaVersion must be v1");
        if (attempt < 1) throw new IllegalArgumentException("attempt must be positive");
    }

    private static void require(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
    }

    public record V1Message(
            @JsonProperty("message_id") String messageId,
            String content,
            java.util.List<Map<String, Object>> attachments) {
        public V1Message {
            require(messageId, "messageId"); require(content, "content");
            attachments = attachments == null ? java.util.List.of() : java.util.List.copyOf(attachments);
        }
    }
}
