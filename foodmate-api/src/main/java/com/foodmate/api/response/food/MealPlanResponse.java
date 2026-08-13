package com.foodmate.api.response.food;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.math.BigDecimal;
import java.time.Instant;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record MealPlanResponse(
        String mealPlanId,
        Long sessionId,
        String planName,
        int people,
        int days,
        BigDecimal budget,
        JsonNode constraints,
        JsonNode daysPlan,
        JsonNode validation,
        String status,
        Instant createdAt,
        Instant updatedAt) {}
