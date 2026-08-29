package com.foodmate.api.response.food;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/** 审批执行结果响应。 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ApprovalExecuteResponse(
        String approvalRequestId, String operation, String status, Long resourceId) {}
