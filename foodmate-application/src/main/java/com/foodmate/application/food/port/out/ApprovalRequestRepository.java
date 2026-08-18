package com.foodmate.application.food.port.out;

import java.time.Instant;
import java.util.List;

/** 写操作确认事实和审计持久化端口。 */
public interface ApprovalRequestRepository {
    ApprovalSnapshot findOwned(long userId, long approvalRequestId);

    ApprovalSnapshot findByIdempotency(long userId, String idempotencyKey);

    int insert(ApprovalWrite approval);

    int markConfirmed(long userId, long approvalRequestId, Instant now);

    int markExpired(long userId, long approvalRequestId, Instant now);

    int markRejected(long userId, long approvalRequestId, Instant now);

    int markFailed(long userId, long approvalRequestId, Instant now);

    int markSupersededForResource(
            long userId,
            String resourceType,
            long resourceId,
            String operation,
            long exceptApprovalRequestId,
            Instant now);

    List<ApprovalSnapshot> findSupersedableForResource(
            long userId,
            String resourceType,
            long resourceId,
            String operation,
            long exceptApprovalRequestId,
            Instant now);

    int markExecuted(long userId, long approvalRequestId, Instant now);

    int updateExecutedResource(long userId, long approvalRequestId, long resourceId, Instant now);

    int insertAudit(AuditWrite audit);

    record ApprovalWrite(
            long approvalRequestId,
            long userId,
            Long sessionId,
            Long agentRunId,
            String resourceType,
            Long resourceId,
            String operation,
            String parametersDigest,
            String requestId,
            String traceId,
            String idempotencyKey,
            Instant expiresAt) {}

    record ApprovalSnapshot(
            long approvalRequestId,
            long userId,
            Long sessionId,
            Long agentRunId,
            String resourceType,
            Long resourceId,
            String operation,
            String parametersDigest,
            String status,
            String requestId,
            String traceId,
            String idempotencyKey,
            Instant expiresAt,
            Instant confirmedAt,
            Instant executedAt) {}

    record AuditWrite(
            long operationAuditId,
            long userId,
            String requestId,
            String traceId,
            String targetType,
            String targetId,
            String action,
            String parametersDigest,
            String idempotencyKey,
            String responseJson) {}
}
