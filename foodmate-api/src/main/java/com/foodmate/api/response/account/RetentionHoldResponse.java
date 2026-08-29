package com.foodmate.api.response.account;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.foodmate.application.retention.service.DataRetentionService;

/** 数据清理法律保留响应。 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record RetentionHoldResponse(
        long holdId, String status, String resourceType, long resourceId, String reasonCode) {
    public static RetentionHoldResponse from(DataRetentionService.HoldResult result) {
        return new RetentionHoldResponse(
                result.holdId(),
                result.status(),
                result.resourceType(),
                result.resourceId(),
                result.reasonCode());
    }
}
