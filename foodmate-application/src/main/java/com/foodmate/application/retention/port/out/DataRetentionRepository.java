package com.foodmate.application.retention.port.out;

import java.time.Instant;

/** Persistence boundary for retention policy, legal hold and purge-plan facts. */
public interface DataRetentionRepository {
    Policy policy(String resourceType);

    ResourceSnapshot resource(String resourceType, long resourceId);

    PurgeRequest purgeRequest(long requestId);

    PurgeRequest purgeRequestByIdempotency(long operatorId, String idempotencyKey);

    PurgeRequest activePurgeRequest(String resourceType, long resourceId);

    int insertPurgeRequest(NewPurgeRequest request);

    int approvePurge(long requestId, long approverId, Instant approvedAt);

    int insertPurgeTask(PurgeTask task);

    java.util.List<PurgeTaskSnapshot> pendingTasks(int limit);

    int leaseTask(long taskId, String owner, String resourceType, long resourceId);

    int markTaskPublished(long taskId, String owner, String messageId);

    int markTaskSucceeded(long taskId, String owner, String errorCode, String errorSummary);

    void retryTask(long taskId, String owner, String errorCode, String errorSummary);

    int applyTaskResult(long taskId, String status, String errorCode, String errorSummary);

    void refreshPurgeRequest(long taskId);

    int insertHold(NewHold hold);

    Hold activeHold(String resourceType, long resourceId);

    Hold hold(long holdId);

    int releaseHold(long holdId, long operatorId, Instant releasedAt);

    record Policy(
            long policyId,
            String resourceType,
            int retentionDays,
            boolean hardDeleteEnabled,
            String policyVersion) {}

    record ResourceSnapshot(
            String resourceType,
            long resourceId,
            boolean deleted,
            Instant deletedAt,
            long revision,
            String storageKey,
            String version) {}

    record PurgeRequest(
            long requestId,
            String resourceType,
            long resourceId,
            long policyId,
            long requestedBy,
            String status,
            Instant eligibleAt,
            Long approvedBy,
            Instant approvedAt,
            int taskCount) {}

    record NewPurgeRequest(
            long requestId,
            String resourceType,
            long resourceId,
            long policyId,
            long requestedBy,
            String idempotencyKey,
            Instant deletedAt,
            Instant eligibleAt) {}

    record PurgeTask(
            long taskId, long requestId, String taskType, String topic, String targetRef) {}

    record PurgeTaskSnapshot(
            long taskId,
            long requestId,
            String resourceType,
            long resourceId,
            String taskType,
            String topic,
            String targetRef,
            String status,
            boolean hardDeleteEnabled) {}

    record Hold(
            long holdId,
            String resourceType,
            long resourceId,
            String reasonCode,
            long placedBy,
            String status,
            Instant placedAt) {}

    record NewHold(
            long holdId, String resourceType, long resourceId, String reasonCode, long placedBy) {}
}
