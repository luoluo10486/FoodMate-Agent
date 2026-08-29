package com.foodmate.api.request.runtime;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.NotBlank;

/** 聊天 AgentRun 创建请求参数。 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ChatRunRequest(@NotBlank String prompt, String sessionId) {}
