package com.foodmate.api.request.food;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

/** 用户复合菜创建请求。 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CompositeDishCreateRequest(
        @NotNull @Size(min = 1, max = 128) String dishName,
        @NotNull @Positive BigDecimal totalServings,
        @NotEmpty @Valid List<Component> components) {
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Component(
            @NotNull @Positive Long nutritionFoodId,
            @NotNull @Size(min = 1, max = 255) String rawName,
            @NotNull @Positive BigDecimal amount,
            @NotNull @Size(min = 1, max = 32) String unit) {}
}
