package com.foodmate.api.request.account;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/** 数据清理法律保留请求参数。 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record RetentionHoldRequest(
        String resourceType,
        long resourceId,
        String reasonCode,
        boolean confirmed,
        String confirmationDigest) {}
