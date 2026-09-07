package com.foodmate.application.food.service;

import com.foodmate.shared.food.enums.MealType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** 手工饮食记录第一纵向切片用例。 */
public interface FoodLogService {
    FoodLogView create(long userId, CreateCommand command);

    /** 使用乐观并发完整替换已有饮食记录的可编辑内容。 */
    FoodLogView update(long userId, long foodLogId, long revision, UpdateCommand command);

    List<FoodLogView> list(long userId, Instant from, Instant to);

    /** 查询当前用户可恢复的软删除饮食记录。 */
    List<FoodLogView> listDeleted(long userId);

    void delete(long userId, long foodLogId, long revision, String idempotencyKey);

    FoodLogView restore(long userId, long foodLogId, long revision, String idempotencyKey);

    record CreateCommand(
            Long sessionId,
            Long agentRunId,
            Instant mealTime,
            MealType mealType,
            String notes,
            String idempotencyKey,
            String source,
            Long mealPlanMealId,
            Long compositeDishId,
            Long compositeDishRevision,
            BigDecimal compositeDishServings,
            List<ItemCommand> items) {
        public CreateCommand(
                Long sessionId,
                Long agentRunId,
                Instant mealTime,
                MealType mealType,
                String notes,
                String idempotencyKey,
                List<ItemCommand> items) {
            this(
                    sessionId,
                    agentRunId,
                    mealTime,
                    mealType,
                    notes,
                    idempotencyKey,
                    "manual",
                    null,
                    null,
                    null,
                    null,
                    items);
        }

        public CreateCommand(
                Long sessionId,
                Long agentRunId,
                Instant mealTime,
                MealType mealType,
                String notes,
                String idempotencyKey,
                String source,
                List<ItemCommand> items) {
            this(
                    sessionId,
                    agentRunId,
                    mealTime,
                    mealType,
                    notes,
                    idempotencyKey,
                    source,
                    null,
                    null,
                    null,
                    null,
                    items);
        }

        /** R2 复合菜调用方的兼容构造函数；计划餐次关联使用完整构造函数。 */
        public CreateCommand(
                Long sessionId,
                Long agentRunId,
                Instant mealTime,
                MealType mealType,
                String notes,
                String idempotencyKey,
                String source,
                Long compositeDishId,
                Long compositeDishRevision,
                BigDecimal compositeDishServings,
                List<ItemCommand> items) {
            this(
                    sessionId,
                    agentRunId,
                    mealTime,
                    mealType,
                    notes,
                    idempotencyKey,
                    source,
                    null,
                    compositeDishId,
                    compositeDishRevision,
                    compositeDishServings,
                    items);
        }

        public CreateCommand {
            source = source == null || source.isBlank() ? "manual" : source;
            items = items == null ? List.of() : List.copyOf(items);
        }
    }

    record UpdateCommand(
            Instant mealTime,
            MealType mealType,
            String notes,
            String idempotencyKey,
            Long mealPlanMealId,
            Long compositeDishId,
            Long compositeDishRevision,
            BigDecimal compositeDishServings,
            List<ItemCommand> items) {
        public UpdateCommand(
                Instant mealTime,
                MealType mealType,
                String notes,
                String idempotencyKey,
                List<ItemCommand> items) {
            this(mealTime, mealType, notes, idempotencyKey, null, null, null, null, items);
        }

        /** R2 复合菜调用方的兼容构造函数。 */
        public UpdateCommand(
                Instant mealTime,
                MealType mealType,
                String notes,
                String idempotencyKey,
                Long compositeDishId,
                Long compositeDishRevision,
                BigDecimal compositeDishServings,
                List<ItemCommand> items) {
            this(
                    mealTime,
                    mealType,
                    notes,
                    idempotencyKey,
                    null,
                    compositeDishId,
                    compositeDishRevision,
                    compositeDishServings,
                    items);
        }

        public UpdateCommand {
            items = items == null ? List.of() : List.copyOf(items);
        }
    }

    record ItemCommand(String rawName, BigDecimal amount, String unit, Long nutritionFoodId) {
        public ItemCommand(String rawName, BigDecimal amount, String unit) {
            this(rawName, amount, unit, null);
        }
    }

    record FoodLogView(
            long foodLogId,
            Long sessionId,
            Long agentRunId,
            Instant mealTime,
            MealType mealType,
            String notes,
            String source,
            Long mealPlanMealId,
            Long compositeDishId,
            Long compositeDishRevision,
            BigDecimal compositeDishServings,
            long revision,
            boolean deleted,
            Instant createdAt,
            Instant updatedAt,
            List<ItemView> items) {
        public FoodLogView(
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
                    null,
                    revision,
                    deleted,
                    createdAt,
                    updatedAt,
                    items);
        }

        /** R2 复合菜响应的兼容构造函数。 */
        public FoodLogView(
                long foodLogId,
                Long sessionId,
                Long agentRunId,
                Instant mealTime,
                MealType mealType,
                String notes,
                String source,
                Long compositeDishId,
                Long compositeDishRevision,
                BigDecimal compositeDishServings,
                long revision,
                boolean deleted,
                Instant createdAt,
                Instant updatedAt,
                List<ItemView> items) {
            this(
                    foodLogId,
                    sessionId,
                    agentRunId,
                    mealTime,
                    mealType,
                    notes,
                    source,
                    null,
                    compositeDishId,
                    compositeDishRevision,
                    compositeDishServings,
                    revision,
                    deleted,
                    createdAt,
                    updatedAt,
                    items);
        }

        public FoodLogView {
            items = items == null ? List.of() : List.copyOf(items);
        }
    }

    record ItemView(
            long foodLogItemId,
            int itemOrder,
            String rawName,
            Long nutritionFoodId,
            BigDecimal amount,
            String unit,
            String nutritionStatus,
            BigDecimal caloriesKcal,
            BigDecimal proteinG,
            BigDecimal fatG,
            BigDecimal carbsG) {
        public ItemView(
                long foodLogItemId,
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
