package com.foodmate.api.response.food;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** 餐食计划响应。 */
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
        long revision,
        boolean deleted,
        Instant createdAt,
        Instant updatedAt,
        List<MealSlot> mealSlots,
        int executableMealCount,
        int completedMealCount,
        BigDecimal completionRatio) {
    public MealPlanResponse(
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
            Instant updatedAt) {
        this(
                mealPlanId,
                sessionId,
                planName,
                people,
                days,
                budget,
                constraints,
                daysPlan,
                validation,
                status,
                1,
                false,
                createdAt,
                updatedAt,
                List.of(),
                0,
                0,
                BigDecimal.ZERO);
    }

    public MealPlanResponse(
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
            long revision,
            boolean deleted,
            Instant createdAt,
            Instant updatedAt) {
        this(
                mealPlanId,
                sessionId,
                planName,
                people,
                days,
                budget,
                constraints,
                daysPlan,
                validation,
                status,
                revision,
                deleted,
                createdAt,
                updatedAt,
                List.of(),
                0,
                0,
                BigDecimal.ZERO);
    }

    public record MealSlot(
            String mealPlanMealId,
            int dayIndex,
            String mealType,
            String mealName,
            JsonNode meal,
            int foodLogCount,
            boolean completed) {}
}
