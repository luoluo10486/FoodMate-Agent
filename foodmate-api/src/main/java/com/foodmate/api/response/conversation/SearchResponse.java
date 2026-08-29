package com.foodmate.api.response.conversation;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

/** 会话搜索结果响应。 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record SearchResponse(String sessionId, String title, String snippet) {}
