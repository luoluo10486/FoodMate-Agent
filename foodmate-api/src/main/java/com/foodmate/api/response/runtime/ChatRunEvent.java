package com.foodmate.api.response.runtime;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.Instant;

/** 聊天 AgentRun 的 SSE 事件响应。 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ChatRunEvent(
        String eventId,
        String runId,
        long eventSeq,
        String state,
        Object payload,
        Instant occurredAt) {}
