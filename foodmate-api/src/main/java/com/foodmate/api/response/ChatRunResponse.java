package com.foodmate.api.response;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ChatRunResponse(
        String runId,
        String dispatchId,
        String status,
        boolean duplicate,
        Long sessionId,
        Long userMessageId) {}
