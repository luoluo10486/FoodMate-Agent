package com.foodmate.application.food.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** 餐食计划的确定性校验和状态流转用例。 */
public interface MealPlanService {
    PlanView create(long userId, CreateCommand command);

    PlanView get(long userId, long mealPlanId);

    List<PlanView> list(long userId);

    PlanView update(long userId, long mealPlanId, long revision, UpdateCommand command);

    void delete(long userId, long mealPlanId, long revision, String idempotencyKey);

    PlanView restore(long userId, long mealPlanId, long revision, String idempotencyKey);

    PlanView validate(long userId, long mealPlanId);

    default PlanView validate(long userId, long mealPlanId, String idempotencyKey) {
        return validate(userId, mealPlanId);
    }

    PlanView validate(long userId, long mealPlanId, long revision, String idempotencyKey);

    PlanView save(long userId, long mealPlanId);

    default PlanView save(long userId, long mealPlanId, String idempotencyKey) {
        return save(userId, mealPlanId);
    }

    PlanView save(long userId, long mealPlanId, long revision, String idempotencyKey);

    ShoppingListView shoppingList(long userId, long mealPlanId);

    record CreateCommand(
            Long sessionId,
            String planName,
            int people,
            int days,
            BigDecimal budget,
            Integer calorieTarget,
            Integer proteinTarget,
            List<String> allergens,
            List<String> dislikes,
            JsonNode daysPlan,
            String idempotencyKey) {
        public CreateCommand(
                Long sessionId,
                String planName,
                int people,
                int days,
                BigDecimal budget,
                Integer calorieTarget,
                Integer proteinTarget,
                List<String> allergens,
                List<String> dislikes,
                JsonNode daysPlan) {
            this(
                    sessionId,
                    planName,
                    people,
                    days,
                    budget,
                    calorieTarget,
                    proteinTarget,
                    allergens,
                    dislikes,
                    daysPlan,
                    null);
        }

        public CreateCommand {
            allergens = allergens == null ? List.of() : List.copyOf(allergens);
            dislikes = dislikes == null ? List.of() : List.copyOf(dislikes);
            daysPlan =
                    daysPlan == null
                            ? com.fasterxml.jackson.databind.node.JsonNodeFactory.instance
                                    .arrayNode()
                            : daysPlan.deepCopy();
        }
    }

    record UpdateCommand(
            String planName,
            int people,
            int days,
            BigDecimal budget,
            Integer calorieTarget,
            Integer proteinTarget,
            List<String> allergens,
            List<String> dislikes,
            JsonNode daysPlan,
            String idempotencyKey) {
        public UpdateCommand {
            allergens = allergens == null ? List.of() : List.copyOf(allergens);
            dislikes = dislikes == null ? List.of() : List.copyOf(dislikes);
            daysPlan =
                    daysPlan == null
                            ? com.fasterxml.jackson.databind.node.JsonNodeFactory.instance
                                    .arrayNode()
                            : daysPlan.deepCopy();
        }
    }

    record PlanView(
            long mealPlanId,
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
        public PlanView(
                long mealPlanId,
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
                    updatedAt);
        }
    }

    record ShoppingListView(
            long shoppingListId,
            long mealPlanId,
            JsonNode items,
            String status,
            Instant createdAt,
            Instant updatedAt) {}
}
