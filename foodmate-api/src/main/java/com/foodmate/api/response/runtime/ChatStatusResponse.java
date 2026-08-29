package com.foodmate.api.response.runtime;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/** 聊天 AgentRun 状态响应。 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ChatStatusResponse(String runId, String status) {}
