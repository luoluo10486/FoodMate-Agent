package com.foodmate.api.response.account;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.Instant;

/** 管理端导出任务状态响应。 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AdminExportStatusResponse(
        long exportJobId,
        String resource,
        String status,
        Instant expiresAt,
        Instant completedAt,
        Instant downloadConsumedAt,
        String failureCode) {}
