package com.foodmate.application.runtime.port.out;

import java.util.List;

public interface DeadLetterRepository {
    void insert(DlqMessage message);

    List<DlqEntry> findPending(int limit);

    long inboxCount(long runId, String eventId);

    List<String> findRunStatuses(long runId);

    int resolve(long dlqId, String state, String note);

    ReplayCandidate findReplayCandidate(long dlqId);

    ReplayOutbox findActiveReplay(long dlqId);

    int insertReplay(ReplayRequest request);

    List<ReplayOutbox> findPendingReplay(int limit);

    int leaseReplay(long replayId, String owner);

    int markReplayPublished(long replayId, String owner, String messageId);

    void retryReplay(long replayId, String owner, String error);

    record DlqMessage(
            long id,
            String group,
            String topic,
            String messageId,
            String messageKey,
            String runId,
            String dispatchId,
            Integer attempt,
            String eventId,
            Long eventSeq,
            String requestHash,
            int reconsumeTimes,
            String errorCode,
            String lastError,
            String payload,
            String payloadText) {}

    record DlqEntry(long id, String runId, String eventId) {}

    record ReplayCandidate(
            long dlqId,
            String consumerGroup,
            String sourceTopic,
            String originalMessageId,
            String messageKey,
            String runId,
            String dispatchId,
            Integer attempt,
            String eventId,
            Long eventSeq,
            String requestHash,
            String payload) {}

    record ReplayRequest(
            long replayId,
            long dlqId,
            long operatorId,
            String idempotencyKey,
            ReplayCandidate candidate) {}

    record ReplayOutbox(
            long replayId,
            long dlqId,
            long operatorId,
            String consumerGroup,
            String sourceTopic,
            String originalMessageId,
            String messageKey,
            String runId,
            String dispatchId,
            Integer attempt,
            String eventId,
            Long eventSeq,
            String requestHash,
            String payload) {}
}
