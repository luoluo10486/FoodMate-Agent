package com.foodmate.api.response.food;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.foodmate.application.food.service.CompositeDishService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** 用户复合菜响应；只返回当前用户可见的营养和组成摘要。 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CompositeDishResponse(
        String compositeDishId,
        String dishName,
        BigDecimal totalServings,
        BigDecimal caloriesKcalPerServing,
        BigDecimal proteinGPerServing,
        BigDecimal fatGPerServing,
        BigDecimal carbsGPerServing,
        String nutritionSource,
        long revision,
        boolean deleted,
        Instant createdAt,
        Instant updatedAt,
        List<Component> components) {
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Component(
            String itemId,
            int itemOrder,
            String nutritionFoodId,
            String rawName,
            BigDecimal amount,
            String unit,
            BigDecimal normalizedAmount,
            String normalizedUnit,
            BigDecimal caloriesKcal,
            BigDecimal proteinG,
            BigDecimal fatG,
            BigDecimal carbsG) {}

    public static CompositeDishResponse map(CompositeDishService.CompositeDishView value) {
        return new CompositeDishResponse(
                Long.toString(value.compositeDishId()),
                value.dishName(),
                value.totalServings(),
                value.caloriesKcalPerServing(),
                value.proteinGPerServing(),
                value.fatGPerServing(),
                value.carbsGPerServing(),
                value.nutritionSource(),
                value.revision(),
                value.deleted(),
                value.createdAt(),
                value.updatedAt(),
                value.components().stream()
                        .map(
                                component ->
                                        new Component(
                                                Long.toString(component.itemId()),
                                                component.itemOrder(),
                                                Long.toString(component.nutritionFoodId()),
                                                component.rawName(),
                                                component.amount(),
                                                component.unit(),
                                                component.normalizedAmount(),
                                                component.normalizedUnit(),
                                                component.caloriesKcal(),
                                                component.proteinG(),
                                                component.fatG(),
                                                component.carbsG()))
                        .toList());
    }
}
