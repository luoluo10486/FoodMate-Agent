package com.foodmate.api.sse;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.foodmate.shared.json.LongIdJsonSerializer;
import java.time.Instant;

/**
 * SSE 事件输出的标准信封。
 */
public record SseEventEnvelope<T>(
        @JsonProperty("event_type") String eventType,
        @JsonProperty("run_id") @JsonSerialize(using = LongIdJsonSerializer.class) Long runId,
        Instant timestamp,
        T payload
) {
    public static <T> SseEventEnvelope<T> of(String eventType, Long runId, T payload) {
        return new SseEventEnvelope<>(eventType, runId, Instant.now(), payload);
    }
}
