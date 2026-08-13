package com.foodmate.api.request.food;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.foodmate.shared.food.enums.MealType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** Full replacement payload for the editable fields of a food log. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record FoodLogUpdateRequest(
        @NotNull Instant mealTime,
        @NotNull MealType mealType,
        @Size(max = 4000) String notes,
        @NotEmpty @Valid List<Item> items) {
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Item(
            @NotNull @Size(min = 1, max = 255) String rawName,
            @NotNull @Positive BigDecimal amount,
            @NotNull @Size(min = 1, max = 32) String unit) {}
}
