package com.foodmate.api.response.food;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** 饮食记录响应。 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record FoodLogResponse(
        String foodLogId,
        String sessionId,
        String agentRunId,
        Instant mealTime,
        String mealType,
        String notes,
        String source,
        String compositeDishId,
        Long compositeDishRevision,
        BigDecimal compositeDishServings,
        long revision,
        boolean deleted,
        Instant createdAt,
        Instant updatedAt,
        List<Item> items) {
    public FoodLogResponse(
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
        this(
                foodLogId,
                sessionId,
                agentRunId,
                mealTime,
                mealType,
                notes,
                source,
                null,
                null,
                null,
                revision,
                deleted,
                createdAt,
                updatedAt,
                items);
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Item(
            String foodLogItemId,
            int itemOrder,
            String rawName,
            String nutritionFoodId,
            BigDecimal amount,
            String unit,
            String nutritionStatus,
            BigDecimal caloriesKcal,
            BigDecimal proteinG,
            BigDecimal fatG,
            BigDecimal carbsG) {
        public Item(
                String foodLogItemId,
                int itemOrder,
                String rawName,
                BigDecimal amount,
                String unit,
                String nutritionStatus,
                BigDecimal caloriesKcal,
                BigDecimal proteinG,
                BigDecimal fatG,
                BigDecimal carbsG) {
            this(
                    foodLogItemId,
                    itemOrder,
                    rawName,
                    null,
                    amount,
                    unit,
                    nutritionStatus,
                    caloriesKcal,
                    proteinG,
                    fatG,
                    carbsG);
        }
    }
}
