package com.foodmate.application.runtime.port.out;

import java.math.BigDecimal;
import java.time.Instant;

/** 预算追加事务所需的持久化端口，锁与 CAS 语义由 Infra Mapper 实现。 */
public interface BudgetExtensionRepository {
    RunRow lockRun(long runId, long userId);

    ExistingExtension findConfirmed(long runId, String digest);

    DispatchResult latestDispatchResult(long runId);

    Snapshot lockLatestSnapshot(long runId);

    int nextExtensionNo(long runId);

    void insertExtension(
            long id,
            long runId,
            int no,
            int tokens,
            BigDecimal cost,
            String digest,
            Instant expiresAt);

    void insertSnapshot(
            long id,
            long runId,
            int revision,
            int tokens,
            BigDecimal cost,
            Snapshot source,
            String digest);

    PreviousDispatch lockPreviousDispatch(long runId);

    void expireDispatch(long dispatchRowId);

    void expireOutbox(long dispatchRowId);

    void insertDispatch(
            long rowId,
            long runId,
            String dispatchId,
            int attempt,
            long epoch,
            String fence,
            Instant deadline);

    void insertOutbox(
            long outboxId,
            long rowId,
            long runId,
            String dispatchId,
            int attempt,
            Instant deadline,
            long epoch,
            String payload,
            String hash);

    void markOutboxQueued(long runId, String dispatchId, int priority);

    void markRunQueued(long runId, long dispatchRowId);

    record RunRow(String status, String resultType, long sessionId) {}

    record ExistingExtension(int tokens, BigDecimal cost, int extensionNo) {}

    record DispatchResult(String dispatchId, int attempt, int revision) {}

    record Snapshot(
            int revision,
            int tokens,
            BigDecimal cost,
            int stepRetries,
            int replans,
            int answerRewrites,
            int totalSteps,
            int modelCalls,
            int queueTimeout,
            int executionTimeout,
            int nodeTimeout,
            int waitingUserTimeout,
            String configVersion) {}

    record PreviousDispatch(long dispatchRowId, int attempt, long epoch, String payload) {}
}
