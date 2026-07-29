package com.foodmate.shared.runtime;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** V1 Python -> Java RunEvent envelope 的不可变 Java 表示。 */
public record V1RunEvent(
        @JsonProperty("schema_version") String schemaVersion,
        @JsonProperty("run_id") String runId,
        @JsonProperty("dispatch_id") String dispatchId,
        int attempt,
        @JsonProperty("event_id") String eventId,
        @JsonProperty("event_seq") long eventSeq,
        @JsonProperty("request_id") String requestId,
        @JsonProperty("trace_id") String traceId,
        @JsonProperty("request_hash") String requestHash,
        @JsonProperty("occurred_at") Instant occurredAt,
        @JsonProperty("event_type") String eventType,
        Map<String, Object> payload) {
    public V1RunEvent {
        require(schemaVersion, "schemaVersion");
        require(runId, "runId");
        require(dispatchId, "dispatchId");
        require(eventId, "eventId");
        require(requestId, "requestId");
        require(traceId, "traceId");
        require(requestHash, "requestHash");
        require(eventType, "eventType");
        Objects.requireNonNull(occurredAt, "occurredAt");
        // Usage events intentionally contain nullable provider/cost fields; Map.copyOf rejects
        // them.
        payload =
                payload == null
                        ? Map.of()
                        : Collections.unmodifiableMap(new LinkedHashMap<>(payload));
        if (!"v1".equals(schemaVersion))
            throw new IllegalArgumentException("schemaVersion must be v1");
        if (attempt < 1 || eventSeq < 1)
            throw new IllegalArgumentException("attempt and eventSeq must be positive");
    }

    private static void require(String value, String name) {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException(name + " must not be blank");
    }
}
