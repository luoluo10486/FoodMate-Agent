package com.foodmate.shared.runtime;

import java.time.Instant;
import java.util.Objects;

public record RunEvent(String eventId, String runId, long eventSeq, State state, String payload, Instant occurredAt) {
    public enum State { DISPATCHED, RUNNING, SUCCEEDED, FAILED, CANCELED }
    public RunEvent {
        require(eventId, "eventId"); require(runId, "runId");
        if (eventSeq < 1) throw new IllegalArgumentException("eventSeq must be positive");
        Objects.requireNonNull(state, "state"); Objects.requireNonNull(occurredAt, "occurredAt");
    }
    private static void require(String value, String name) { if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank"); }
}
