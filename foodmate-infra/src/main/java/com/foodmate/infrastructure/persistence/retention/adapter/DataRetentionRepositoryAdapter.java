package com.foodmate.infrastructure.persistence.retention.adapter;

import com.foodmate.application.retention.port.out.DataRetentionRepository;
import com.foodmate.infrastructure.persistence.retention.DataRetentionMapper;
import java.time.Instant;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

/** PostgreSQL implementation of the retention governance port. */
@Repository
@Profile("local")
public class DataRetentionRepositoryAdapter implements DataRetentionRepository {
    private final DataRetentionMapper mapper;

    public DataRetentionRepositoryAdapter(DataRetentionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Policy policy(String resourceType) {
        return mapper.policy(resourceType);
    }

    @Override
    public ResourceSnapshot resource(String resourceType, long resourceId) {
        return mapper.resource(resourceType, resourceId);
    }

    @Override
    public PurgeRequest purgeRequest(long requestId) {
        return mapper.purgeRequest(requestId);
    }

    @Override
    public PurgeRequest purgeRequestByIdempotency(long operatorId, String idempotencyKey) {
        return mapper.purgeRequestByIdempotency(operatorId, idempotencyKey);
    }

    @Override
    public PurgeRequest activePurgeRequest(String resourceType, long resourceId) {
        return mapper.activePurgeRequest(resourceType, resourceId);
    }

    @Override
    public int insertPurgeRequest(NewPurgeRequest request) {
        return mapper.insertPurgeRequest(request);
    }

    @Override
    public int approvePurge(long requestId, long approverId, Instant approvedAt) {
        return mapper.approvePurge(requestId, approverId, approvedAt);
    }

    @Override
    public int insertPurgeTask(PurgeTask task) {
        return mapper.insertPurgeTask(task);
    }

    @Override
    public int insertHold(NewHold hold) {
        return mapper.insertHold(hold);
    }

    @Override
    public Hold activeHold(String resourceType, long resourceId) {
        return mapper.activeHold(resourceType, resourceId);
    }

    @Override
    public Hold hold(long holdId) {
        return mapper.hold(holdId);
    }

    @Override
    public int releaseHold(long holdId, long operatorId, Instant releasedAt) {
        return mapper.releaseHold(holdId, operatorId, releasedAt);
    }
}
