package com.foodmate.application.runtime.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** AgentRun 命令、上下文和 Outbox 的持久化端口。 */
public interface AgentRunCommandStore {
    Long waitingRun(long sessionId);

    void insertRun(long runId, long sessionId, String traceId, long userId, Long parentRunId);

    void bindMessage(long runId, long messageId);

    List<Map<String, Object>> recentMessages(long sessionId);

    Map<String, Object> summary(long sessionId);

    List<Map<String, Object>> memories(long userId);

    void insertDispatch(long id, long runId, String dispatchId, String fence, Instant deadline);

    void insertOutbox(
            long id,
            long dispatchRowId,
            long runId,
            String dispatchId,
            Instant deadline,
            String payload,
            String hash);

    void queueOutbox(long runId, String dispatchId, int priority);

    void activateDispatch(long runId, long dispatchRowId);

    int supersede(long parentRunId, long continuationRunId);

    void supersedeDispatch(long runId);

    void expireOutbox(long runId);

    long lockNextSseSequence(long runId);

    void insertSse(long id, long runId, String eventId, long seq, String sourceKey, String payload);

    void updateSseSequence(long runId, long seq);

    void insertBudget(
            long id,
            long runId,
            int tokens,
            BigDecimal cost,
            int retries,
            int replans,
            int rewrites,
            int steps,
            int calls,
            int queueTimeout,
            int executionTimeout,
            int nodeTimeout,
            int waitingTimeout,
            String version);
}
