package com.foodmate.application.runtime.port.out;

import com.foodmate.shared.runtime.V1RunEvent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public interface RuntimeEventRepository {
    boolean runExists(long runId);

    String eventHash(long runId, String eventId);

    DispatchRow dispatch(long runId, String dispatchId);

    ActiveDispatch activeDispatch(long runId);

    void insertEvent(long id, long runId, V1RunEvent event, String payload);

    void updateDispatch(long runId, String dispatchId, long seq, String status);

    void updateRun(long runId, String status, String result);

    void touchRun(long runId);

    void setResultType(long runId, String type);

    void acknowledgeCancel(long runId, String dispatchId);

    void resolveCancel(long runId, String dispatchId);

    long lockNextSseSequence(long runId);

    void insertSse(
            long id,
            long runId,
            String sseId,
            long seq,
            String source,
            String type,
            String payload);

    void updateSseSequence(long runId, long seq);

    void promoteOutbox(String runId);

    List<EventRow> events(long runId);

    List<SseRow> sseEvents(long runId, long after);

    Long cursor(long runId, String cursor);

    String status(long runId);

    boolean owned(long runId, long userId);

    void reject(
            String id, long runId, V1RunEvent event, String reason, String code, String envelope);

    void insertUsage(
            long id,
            V1RunEvent event,
            String scene,
            String provider,
            String model,
            String usage,
            Integer latency,
            BigDecimal cost,
            String status,
            String routeVersion,
            String priceVersion,
            String budgetPolicyVersion);

    boolean assistantExists(long runId);

    RunOwner lockOwner(long runId);

    int lockMessageSequence(long sessionId);

    int nextMessageSequence(long sessionId);

    void insertAssistant(
            long id, long runId, RunOwner owner, String text, String payload, int sequence);

    void touchSession(long sessionId);

    boolean publicCitationVisible(long documentId, String version);

    record DispatchRow(long id, long lastEventSeq, String state, int attempt) {}

    record ActiveDispatch(String dispatchId, int attempt, long lastEventSeq) {}

    record EventRow(
            String eventId,
            String dispatchId,
            int attempt,
            long seq,
            String type,
            Instant occurredAt,
            String payload,
            String hash) {}

    record SseRow(long seq, String id, String type, String payload) {}

    record RunOwner(long sessionId, long userId) {}
}
