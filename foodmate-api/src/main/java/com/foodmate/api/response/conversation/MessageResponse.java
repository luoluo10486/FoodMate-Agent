package com.foodmate.api.response.conversation;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record MessageResponse(
        String messageId,
        String sessionId,
        String agentRunId,
        String role,
        String content,
        String structuredPayload,
        int sequenceNo,
        java.time.Instant createdAt) {}
