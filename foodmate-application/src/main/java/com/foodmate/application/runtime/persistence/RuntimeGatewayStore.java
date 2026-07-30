package com.foodmate.application.runtime.persistence;

import com.foodmate.shared.runtime.RunEvent;
import java.time.Instant;
import java.util.List;

public interface RuntimeGatewayStore {
    String dispatchFingerprint(String id);

    String cancelFingerprint(String id);

    void createRun(String runId, String status);

    void insertDispatch(String id, String runId, String fingerprint);

    void insertCancel(String id, String runId, String fingerprint);

    String status(String runId);

    void updateStatus(String runId, String status);

    String eventFingerprint(String runId, String eventId);

    EventHead latestEvent(String runId);

    void insertEvent(RunEvent event, String fingerprint, String payload);

    List<EventRow> events(String runId);

    boolean runExists(String runId);

    void registerAgentRun(
            long runId, long sessionId, long messageId, String status, String traceId, long userId);

    Long owner(long runId);

    void updateAgentRun(long runId, String status, String payload, String error);

    String agentStatus(long runId);

    record EventHead(long seq, String state) {}

    record EventRow(String id, long seq, String state, String payload, Instant occurredAt) {}
}
