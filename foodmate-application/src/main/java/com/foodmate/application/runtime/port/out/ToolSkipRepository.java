package com.foodmate.application.runtime.port.out;

import java.time.Instant;
import java.util.List;

/** 单个工具步骤跳过请求的持久化边界。 */
public interface ToolSkipRepository {
    SkipRecord findByProposalId(String proposalId);

    void insertRequested(NewSkip skip);

    List<PendingSkip> findRequested(int limit);

    int markDispatched(long rowId, String transport, String messageId);

    int markApplied(String proposalId);

    record NewSkip(
            long rowId,
            String skipId,
            long runId,
            String proposalId,
            String invocationId,
            String toolName,
            String dispatchId,
            int attempt,
            String requestHash,
            String proposalRequestHash,
            String reason,
            Instant requestedAt) {}

    record PendingSkip(
            long rowId,
            String skipId,
            String runId,
            String proposalId,
            String invocationId,
            String toolName,
            String dispatchId,
            int attempt,
            String requestHash,
            String proposalRequestHash,
            String reason,
            Instant requestedAt) {}

    record SkipRecord(
            long rowId,
            String skipId,
            String runId,
            String proposalId,
            String invocationId,
            String toolName,
            String dispatchId,
            int attempt,
            String requestHash,
            String proposalRequestHash,
            String reason,
            String status,
            String transport,
            String messageId) {}
}
