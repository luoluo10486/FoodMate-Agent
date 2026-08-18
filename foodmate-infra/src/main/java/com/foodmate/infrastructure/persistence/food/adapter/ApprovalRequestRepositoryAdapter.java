package com.foodmate.infrastructure.persistence.food.adapter;

import com.foodmate.application.common.port.out.OperationAuditPort;
import com.foodmate.application.food.port.out.ApprovalRequestRepository;
import com.foodmate.infrastructure.persistence.food.ApprovalRequestMapper;
import java.time.Instant;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

/** 将审批事实 MyBatis 映射暴露为 application 端口。 */
@Repository
@Profile("local")
public class ApprovalRequestRepositoryAdapter implements ApprovalRequestRepository {
    private final ApprovalRequestMapper mapper;
    private final OperationAuditPort audit;

    public ApprovalRequestRepositoryAdapter(
            ApprovalRequestMapper mapper, OperationAuditPort audit) {
        this.mapper = mapper;
        this.audit = audit;
    }

    @Override
    public ApprovalSnapshot findOwned(long userId, long approvalRequestId) {
        return mapper.findOwned(userId, approvalRequestId);
    }

    @Override
    public ApprovalSnapshot findByIdempotency(long userId, String idempotencyKey) {
        return mapper.findByIdempotency(userId, idempotencyKey);
    }

    @Override
    public int insert(ApprovalWrite approval) {
        return mapper.insert(approval);
    }

    @Override
    public int markConfirmed(long userId, long approvalRequestId, Instant now) {
        return mapper.markConfirmed(userId, approvalRequestId, now);
    }

    @Override
    public int markExpired(long userId, long approvalRequestId, Instant now) {
        return mapper.markExpired(userId, approvalRequestId, now);
    }

    @Override
    public int markRejected(long userId, long approvalRequestId, Instant now) {
        return mapper.markRejected(userId, approvalRequestId, now);
    }

    @Override
    public int markFailed(long userId, long approvalRequestId, Instant now) {
        return mapper.markFailed(userId, approvalRequestId, now);
    }

    @Override
    public int markSupersededForResource(
            long userId,
            String resourceType,
            long resourceId,
            String operation,
            long exceptApprovalRequestId,
            Instant now) {
        return mapper.markSupersededForResource(
                userId, resourceType, resourceId, operation, exceptApprovalRequestId, now);
    }

    @Override
    public List<ApprovalSnapshot> findSupersedableForResource(
            long userId,
            String resourceType,
            long resourceId,
            String operation,
            long exceptApprovalRequestId,
            Instant now) {
        return mapper.findSupersedableForResource(
                userId, resourceType, resourceId, operation, exceptApprovalRequestId, now);
    }

    @Override
    public int markExecuted(long userId, long approvalRequestId, Instant now) {
        return mapper.markExecuted(userId, approvalRequestId, now);
    }

    @Override
    public int updateExecutedResource(
            long userId, long approvalRequestId, long resourceId, Instant now) {
        return mapper.updateExecutedResource(userId, approvalRequestId, resourceId, now);
    }

    @Override
    public int insertAudit(AuditWrite audit) {
        return this.audit.insert(
                new OperationAuditPort.AuditRecord(
                        audit.operationAuditId(),
                        audit.userId(),
                        audit.requestId(),
                        audit.traceId(),
                        audit.targetType(),
                        audit.targetId(),
                        audit.action(),
                        "success",
                        null,
                        "{}",
                        audit.responseJson(),
                        audit.parametersDigest(),
                        audit.idempotencyKey()));
    }
}
