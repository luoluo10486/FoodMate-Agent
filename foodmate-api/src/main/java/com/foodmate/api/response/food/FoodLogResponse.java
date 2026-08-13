package com.foodmate.api.response.food;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record FoodLogResponse(
        String foodLogId,
        String sessionId,
        String agentRunId,
        Instant mealTime,
        String mealType,
        String notes,
        String source,
        long revision,
        boolean deleted,
        Instant createdAt,
        Instant updatedAt,
        List<Item> items) {
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Item(
            String foodLogItemId,
            int itemOrder,
            String rawName,
            BigDecimal amount,
            String unit,
            String nutritionStatus,
            BigDecimal caloriesKcal,
            BigDecimal proteinG,
            BigDecimal fatG,
            BigDecimal carbsG) {}
}
