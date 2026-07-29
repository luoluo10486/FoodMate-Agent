package com.foodmate.application.runtime.persistence;

import java.util.List;

public interface DlqStore {
    void insert(DlqMessage message);

    List<DlqEntry> findPending(int limit);

    long inboxCount(long runId, String eventId);

    List<String> findRunStatuses(long runId);

    int resolve(long dlqId, String state, String note);

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
            String payload) {}

    record DlqEntry(long id, String runId, String eventId) {}
}
