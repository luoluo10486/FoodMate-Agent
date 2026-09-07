package com.foodmate.application.food.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** 用户复合菜基础用例；不提供公共菜谱发布和烹饪步骤管理。 */
public interface CompositeDishService {
    CompositeDishView create(long userId, CreateCommand command);

    CompositeDishView get(long userId, long compositeDishId);

    List<CompositeDishView> list(long userId);

    CompositeDishView update(
            long userId, long compositeDishId, long revision, UpdateCommand command);

    void delete(long userId, long compositeDishId, long revision, String idempotencyKey);

    record CreateCommand(
            String dishName,
            BigDecimal totalServings,
            List<ComponentCommand> components,
            String idempotencyKey) {
        public CreateCommand(
                String dishName, BigDecimal totalServings, List<ComponentCommand> components) {
            this(dishName, totalServings, components, null);
        }

        public CreateCommand {
            components = components == null ? List.of() : List.copyOf(components);
        }
    }

    record UpdateCommand(
            String dishName,
            BigDecimal totalServings,
            List<ComponentCommand> components,
            String idempotencyKey) {
        public UpdateCommand {
            components = components == null ? List.of() : List.copyOf(components);
        }
    }

    record ComponentCommand(long nutritionFoodId, String rawName, BigDecimal amount, String unit) {}

    record CompositeDishView(
            long compositeDishId,
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
            List<ComponentView> components) {}

    record ComponentView(
            long itemId,
            int itemOrder,
            long nutritionFoodId,
            String rawName,
            BigDecimal amount,
            String unit,
            BigDecimal normalizedAmount,
            String normalizedUnit,
            BigDecimal caloriesKcal,
            BigDecimal proteinG,
            BigDecimal fatG,
            BigDecimal carbsG) {}
}
