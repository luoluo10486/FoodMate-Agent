package com.foodmate.application.runtime.persistence;

import java.time.Instant;
import java.util.List;

/** Java-side reconciliation port for resuming a Run from a Python checkpoint. */
public interface RuntimeRecoveryStore {
    RecoveryRun lockRun(long runId, long userId);

    List<String> completedInvocationIds(long runId);

    void expireDispatch(long dispatchRowId);

    void expireOutbox(long dispatchRowId);

    void insertDispatch(
            long rowId,
            long runId,
            String dispatchId,
            int attempt,
            long epoch,
            String fencingToken,
            Instant deadline);

    void insertOutbox(
            long outboxId,
            long dispatchRowId,
            long runId,
            String dispatchId,
            int attempt,
            Instant deadline,
            long epoch,
            String payload,
            String requestHash);

    void markOutboxQueued(long runId, String dispatchId, int priority);

    void markRunQueued(long runId, long dispatchRowId);

    record RecoveryRun(
            String status,
            long sessionId,
            long dispatchRowId,
            String previousDispatchId,
            int previousAttempt,
            long previousEpoch,
            Instant deadline,
            int budgetRevision,
            String payload) {}

    record RecoveryRequest(
            long userId,
            long runId,
            int checkpointVersion,
            String checkpointDigest,
            List<String> completedInvocationIds) {
        public RecoveryRequest {
            completedInvocationIds =
                    completedInvocationIds == null
                            ? List.of()
                            : List.copyOf(completedInvocationIds);
        }
    }
}
