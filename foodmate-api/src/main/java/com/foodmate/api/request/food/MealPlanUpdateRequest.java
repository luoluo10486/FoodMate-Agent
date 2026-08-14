package com.foodmate.api.request.food;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record MealPlanUpdateRequest(
        @Size(max = 128) String planName,
        @Min(1) @Max(20) int people,
        @Min(1) @Max(7) int days,
        @PositiveOrZero BigDecimal budget,
        @PositiveOrZero Integer calorieTarget,
        @PositiveOrZero Integer proteinTarget,
        List<@Size(min = 1, max = 128) String> allergens,
        List<@Size(min = 1, max = 128) String> dislikes,
        JsonNode daysPlan) {}
