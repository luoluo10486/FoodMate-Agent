package com.foodmate.api.response.runtime;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AgentFeedbackResponse(
        String feedbackId,
        String runId,
        String messageId,
        boolean helpful,
        List<String> reasonCodes,
        boolean highRisk,
        String idempotencyKey) {}
