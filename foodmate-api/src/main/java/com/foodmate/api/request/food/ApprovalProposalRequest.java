package com.foodmate.api.request.food;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** 饮食业务审批提案请求参数。 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ApprovalProposalRequest(
        Long sessionId,
        Long agentRunId,
        @NotBlank String operation,
        @NotBlank String resourceType,
        Long resourceId,
        @NotNull JsonNode parameters,
        @NotBlank String idempotencyKey,
        @Min(60) @Max(3600) long expiresInSeconds) {}
