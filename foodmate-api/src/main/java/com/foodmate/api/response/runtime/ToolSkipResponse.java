package com.foodmate.api.response.runtime;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.foodmate.application.runtime.service.ToolSkipService;

/** 单个工具步骤跳过请求的浏览器响应。 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ToolSkipResponse(
        String runId,
        String proposalId,
        String skipId,
        String dispatchId,
        int attempt,
        String status) {
    public static ToolSkipResponse from(ToolSkipService.SkipResult result) {
        return new ToolSkipResponse(
                result.runId(),
                result.proposalId(),
                result.skipId(),
                result.dispatchId(),
                result.attempt(),
                result.status());
    }
}
