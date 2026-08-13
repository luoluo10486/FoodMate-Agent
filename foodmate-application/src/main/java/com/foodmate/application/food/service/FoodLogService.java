package com.foodmate.application.food.service;

import com.foodmate.shared.food.enums.MealType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** 手工饮食记录第一纵向切片用例。 */
public interface FoodLogService {
    FoodLogView create(long userId, CreateCommand command);

    /** Replaces the editable content of an existing food log using optimistic concurrency. */
    FoodLogView update(long userId, long foodLogId, long revision, UpdateCommand command);

    List<FoodLogView> list(long userId, Instant from, Instant to);

    void delete(long userId, long foodLogId, long revision, String idempotencyKey);

    FoodLogView restore(long userId, long foodLogId, long revision, String idempotencyKey);

    record CreateCommand(
            Long sessionId,
            Long agentRunId,
            Instant mealTime,
            MealType mealType,
            String notes,
            String idempotencyKey,
            List<ItemCommand> items) {
        public CreateCommand {
            items = items == null ? List.of() : List.copyOf(items);
        }
    }

    record UpdateCommand(
            Instant mealTime,
            MealType mealType,
            String notes,
            String idempotencyKey,
            List<ItemCommand> items) {
        public UpdateCommand {
            items = items == null ? List.of() : List.copyOf(items);
        }
    }

    record ItemCommand(String rawName, BigDecimal amount, String unit) {}

    record FoodLogView(
            long foodLogId,
            Long sessionId,
            Long agentRunId,
            Instant mealTime,
            MealType mealType,
            String notes,
            String source,
            long revision,
            boolean deleted,
            Instant createdAt,
            Instant updatedAt,
            List<ItemView> items) {
        public FoodLogView {
            items = items == null ? List.of() : List.copyOf(items);
        }
    }

    record ItemView(
            long foodLogItemId,
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
