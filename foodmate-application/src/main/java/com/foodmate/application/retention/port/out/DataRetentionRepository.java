package com.foodmate.application.retention.port.out;

import java.time.Instant;

/** 数据保留策略、法律保全和清理计划事实的持久化边界。 */
public interface DataRetentionRepository {
    /** 读取指定资源类型的有效保留策略。 */
    Policy policy(String resourceType);

    /** 读取清理前所需的软删除和版本事实。 */
    ResourceSnapshot resource(String resourceType, long resourceId);

    /** 按持久化标识读取清理请求。 */
    PurgeRequest purgeRequest(long requestId);

    /** 查找操作者范围内用于幂等重放的历史清理请求。 */
    PurgeRequest purgeRequestByIdempotency(long operatorId, String idempotencyKey);

    /** 查找已覆盖指定资源的有效清理请求。 */
    PurgeRequest activePurgeRequest(String resourceType, long resourceId);

    /** 读取面向操作者的清理前置检查所需的非敏感任务状态。 */
    java.util.List<PurgeTaskState> purgeTaskStates(long requestId);

    /** 创建清理请求，但不删除任何资源数据。 */
    int insertPurgeRequest(NewPurgeRequest request);

    /** 记录经过独立授权的清理审批事实。 */
    int approvePurge(long requestId, long approverId, Instant approvedAt);

    /** 创建一个对象、向量或数据库清理任务。 */
    int insertPurgeTask(PurgeTask task);

    /** 读取当前可领取投递尝试的任务。 */
    java.util.List<PurgeTaskSnapshot> pendingTasks(int limit);

    /** 为工作进程领取一个清理任务。 */
    int leaseTask(long taskId, String owner, String resourceType, long resourceId);

    /** 读取用于校验外部结果的不可变任务上下文。 */
    PurgeTaskContext purgeTaskContext(long taskId);

    /** 记录清理任务消息已发布。 */
    int markTaskPublished(long taskId, String owner, String messageId);

    /** 记录清理任务成功完成。 */
    int markTaskSucceeded(long taskId, String owner, String errorCode, String errorSummary);

    /** 将失败的清理任务安排到有界重试队列。 */
    void retryTask(long taskId, String owner, String errorCode, String errorSummary);

    /** 幂等应用工作进程结果并刷新清理请求状态。 */
    int applyTaskResult(long taskId, String status, String errorCode, String errorSummary);

    /** 在变更任务状态前持久化一条安全的清理执行事实。 */
    int insertPurgeTaskResult(PurgeTaskResult result);

    /** 根据任务状态重新计算清理请求状态。 */
    void refreshPurgeRequest(long taskId);

    /** 设置法律保全，但不修改被保全资源。 */
    int insertHold(NewHold hold);

    /** 读取资源的有效保全记录（如存在）。 */
    Hold activeHold(String resourceType, long resourceId);

    /** 按标识读取法律保全记录。 */
    Hold hold(long holdId);

    /** 解除法律保全并记录解除操作者及时间。 */
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

    record PurgeTaskContext(
            long taskId,
            long requestId,
            String resourceType,
            long resourceId,
            String taskType,
            String version,
            int attemptCount) {}

    /** 结果账本行；有意排除对象键和向量载荷。 */
    record PurgeTaskResult(
            long resultId,
            long taskId,
            long requestId,
            String resourceType,
            long resourceId,
            String taskType,
            String version,
            String status,
            String backend,
            int deletedCount,
            boolean verifiedAbsent,
            String messageId,
            String resultDigest,
            String errorCode,
            String errorSummary) {}

    /** 安全任务状态；目标引用和存储细节不得越过此边界。 */
    record PurgeTaskState(String taskType, String status, int attemptCount, String lastErrorCode) {}

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
