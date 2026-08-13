package com.foodmate.application.food.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** 餐食计划的确定性校验和状态流转用例。 */
public interface MealPlanService {
    PlanView create(long userId, CreateCommand command);

    PlanView validate(long userId, long mealPlanId);

    PlanView save(long userId, long mealPlanId);

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
            JsonNode daysPlan) {
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
            Instant createdAt,
            Instant updatedAt) {}

    record ShoppingListView(
            long shoppingListId,
            long mealPlanId,
            JsonNode items,
            String status,
            Instant createdAt,
            Instant updatedAt) {}
}
