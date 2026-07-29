package com.foodmate.application.runtime.persistence;

import java.time.Instant;
import java.util.List;

public interface CancellationStore {
    ActiveDispatch findActiveDispatch(long runId);

    void insertRequested(NewCancellation cancellation);

    int incrementCancellationEpoch(long runId);

    List<PendingCancellation> findRequested(int limit);

    int markDispatched(long cancellationId, String transport, String messageId);

    record ActiveDispatch(String dispatchId, int attempt, String runStatus) {}

    record NewCancellation(
            long id,
            long runId,
            String cancelId,
            String dispatchId,
            String requestHash,
            String reason,
            Instant requestedAt) {}

    record PendingCancellation(
            long id,
            String runId,
            String cancelId,
            String dispatchId,
            int attempt,
            String requestHash,
            String reason,
            Instant requestedAt) {}
}
