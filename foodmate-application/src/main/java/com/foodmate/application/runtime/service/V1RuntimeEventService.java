package com.foodmate.application.runtime.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.foodmate.shared.runtime.V1RunEvent;
import java.util.List;

/** Validates, persists, and exposes acknowledged V1 runtime events. */
public interface V1RuntimeEventService {
    EventResult accept(V1RunEvent event);

    List<V1RunEvent> events(String runId);

    boolean exists(String runId);

    List<SseRecord> sseEvents(String runId, long afterSequence);

    long cursorFor(String runId, String cursor);

    String status(String runId);

    void requireRunOwner(String runId, long userId);

    record EventResult(String runId, String eventId, boolean duplicate, String status) {}

    record SseRecord(
            long streamSeq,
            String sseEventId,
            String eventType,
            JsonNode payload,
            boolean terminal) {}
}
