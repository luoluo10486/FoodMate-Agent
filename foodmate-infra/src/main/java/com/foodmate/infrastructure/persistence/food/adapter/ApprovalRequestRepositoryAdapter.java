package com.foodmate.infrastructure.persistence.food.adapter;

import com.foodmate.application.food.port.out.ApprovalRequestRepository;
import com.foodmate.infrastructure.persistence.food.ApprovalRequestMapper;
import java.time.Instant;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

/** 将审批事实 MyBatis 映射暴露为 application 端口。 */
@Repository
@Profile("local")
public class ApprovalRequestRepositoryAdapter implements ApprovalRequestRepository {
    private final ApprovalRequestMapper mapper;

    public ApprovalRequestRepositoryAdapter(ApprovalRequestMapper mapper) {
        this.mapper = mapper;
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
        return mapper.insertAudit(audit);
    }
}
