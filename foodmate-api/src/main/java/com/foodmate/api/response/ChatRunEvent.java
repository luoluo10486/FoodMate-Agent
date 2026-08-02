package com.foodmate.api.response;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.Instant;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ChatRunEvent(
        String eventId,
        String runId,
        long eventSeq,
        String state,
        Object payload,
        Instant occurredAt) {}
