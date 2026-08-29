package com.foodmate.shared.runtime;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.Instant;
import java.util.Objects;

/** 为兼容字符串 Run 网关而保留的旧版 Runtime 事件。 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record RunEvent(
        String eventId,
        String runId,
        long eventSeq,
        State state,
        Object payload,
        Instant occurredAt) {
    public enum State {
        DISPATCHED,
        RUNNING,
        SUCCEEDED,
        FAILED,
        CANCELED
    }

    public RunEvent {
        require(eventId, "eventId");
        require(runId, "runId");
        if (eventSeq < 1) throw new IllegalArgumentException("eventSeq must be positive");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }

    private static void require(String value, String name) {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException(name + " must not be blank");
    }
}
