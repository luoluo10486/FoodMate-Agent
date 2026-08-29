package com.foodmate.api.response.food;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** 饮食营养分析响应。 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record NutritionAnalysisResponse(
        String range,
        Instant from,
        Instant to,
        int totalItems,
        int matchedItems,
        BigDecimal coverage,
        BigDecimal caloriesKcal,
        BigDecimal proteinG,
        BigDecimal fatG,
        BigDecimal carbsG,
        Integer calorieTarget,
        Integer proteinTarget,
        boolean incomplete,
        List<String> unmatchedNames,
        String disclaimer) {}
