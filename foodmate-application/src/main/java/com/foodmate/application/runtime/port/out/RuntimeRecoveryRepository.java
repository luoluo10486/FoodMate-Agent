package com.foodmate.application.runtime.port.out;

import java.time.Instant;
import java.util.List;

/** Java 侧从 Python checkpoint 恢复 AgentRun 的对账端口。 */
public interface RuntimeRecoveryRepository {
    RecoveryRun lockRun(long runId, long userId);

    /** Reads the last checkpoint fact acknowledged by the Java event inbox. */
    CheckpointFact latestCheckpoint(long runId, String dispatchId);

    List<String> completedInvocationIds(long runId);

    /** Reads idempotently completed Tool Results that can be replayed after Runtime restart. */
    List<String> completedToolResults(long runId);

    /** Finds active tool-wait dispatches that stopped producing events before a Runtime restart. */
    List<RecoveryCandidate> findStaleToolWaitRuns(int staleSeconds, int limit);

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

    record CheckpointFact(
            int version,
            String digest,
            int budgetRevision,
            String currentNode,
            String completedInvocationIdsJson) {}

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

    record RecoveryCandidate(long runId, long userId) {}
}
