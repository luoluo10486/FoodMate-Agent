package com.foodmate.application.runtime.port.out;

import java.util.List;

public interface AdmissionReconciliationRepository {
    List<RunRef> findQueueExpired(int timeoutSeconds, int limit);

    /** Finds queued rows whose Redis permit was promoted before the database update completed. */
    List<RunRef> findQueued(int limit);

    List<RunRef> findExecutionExpired(int limit);

    int failRun(long agentRunId, String code, String resultJson);

    int expireDispatches(long agentRunId);

    int failOutboxes(long agentRunId, String code);

    Long nextSseSequence(long agentRunId);

    void insertFailedEvent(
            long eventId,
            long agentRunId,
            String sseEventId,
            long sequence,
            String sourceKey,
            String payload);

    void updateSseSequence(long agentRunId, long sequence);

    void promoteOutboxes(List<String> runIds);

    record RunRef(long agentRunId, String runId) {}
}
