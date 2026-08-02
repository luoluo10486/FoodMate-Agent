package com.foodmate.api.response.conversation;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.Instant;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record SessionResponse(
        String sessionId,
        String userId,
        String title,
        String mode,
        String status,
        Instant lastMessageAt) {}
