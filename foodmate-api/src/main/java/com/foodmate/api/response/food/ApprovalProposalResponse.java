package com.foodmate.api.response.food;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.Instant;

/** 审批提案状态响应。 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ApprovalProposalResponse(
        String approvalRequestId,
        String operation,
        String resourceType,
        Long resourceId,
        String parametersDigest,
        String status,
        Instant expiresAt,
        Instant confirmedAt,
        Instant executedAt) {}
