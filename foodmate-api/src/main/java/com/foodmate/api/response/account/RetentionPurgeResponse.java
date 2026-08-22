package com.foodmate.api.response.account;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.foodmate.application.retention.service.DataRetentionService;
import java.time.Instant;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record RetentionPurgeResponse(
        long requestId,
        String status,
        String resourceType,
        long resourceId,
        Instant eligibleAt,
        int taskCount) {
    public static RetentionPurgeResponse from(DataRetentionService.PurgeResult result) {
        return new RetentionPurgeResponse(
                result.requestId(),
                result.status(),
                result.resourceType(),
                result.resourceId(),
                result.eligibleAt(),
                result.taskCount());
    }
}
