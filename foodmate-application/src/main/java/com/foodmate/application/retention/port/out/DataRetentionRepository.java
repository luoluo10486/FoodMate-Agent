package com.foodmate.application.retention.port.out;

import java.time.Instant;

/** Persistence boundary for retention policy, legal hold and purge-plan facts. */
public interface DataRetentionRepository {
    /** Reads the active retention policy for one resource type. */
    Policy policy(String resourceType);

    /** Reads the soft-deletion and version facts needed before purge. */
    ResourceSnapshot resource(String resourceType, long resourceId);

    /** Reads one purge request by its durable identifier. */
    PurgeRequest purgeRequest(long requestId);

    /** Finds a prior purge request for operator-scoped idempotency replay. */
    PurgeRequest purgeRequestByIdempotency(long operatorId, String idempotencyKey);

    /** Finds an active purge request that already covers a resource. */
    PurgeRequest activePurgeRequest(String resourceType, long resourceId);

    /** Creates a purge request without deleting any resource data. */
    int insertPurgeRequest(NewPurgeRequest request);

    /** Records the separately authorized purge approval. */
    int approvePurge(long requestId, long approverId, Instant approvedAt);

    /** Creates one object, vector, or database purge task. */
    int insertPurgeTask(PurgeTask task);

    /** Reads tasks eligible for a leased delivery attempt. */
    java.util.List<PurgeTaskSnapshot> pendingTasks(int limit);

    /** Claims one purge task for a worker. */
    int leaseTask(long taskId, String owner, String resourceType, long resourceId);

    /** Records publication of a purge task message. */
    int markTaskPublished(long taskId, String owner, String messageId);

    /** Records successful completion of a purge task. */
    int markTaskSucceeded(long taskId, String owner, String errorCode, String errorSummary);

    /** Schedules a failed purge task for a bounded retry. */
    void retryTask(long taskId, String owner, String errorCode, String errorSummary);

    /** Applies a worker result idempotently and refreshes the request state. */
    int applyTaskResult(long taskId, String status, String errorCode, String errorSummary);

    /** Recomputes a purge request from its task states. */
    void refreshPurgeRequest(long taskId);

    /** Places a legal hold without changing the held resource. */
    int insertHold(NewHold hold);

    /** Reads the active hold for a resource, if any. */
    Hold activeHold(String resourceType, long resourceId);

    /** Reads a hold by identifier. */
    Hold hold(long holdId);

    /** Releases a hold and records the releasing operator and time. */
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
