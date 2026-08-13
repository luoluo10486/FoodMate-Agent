package com.foodmate.application.food.port.out;

import java.math.BigDecimal;
import java.time.Instant;

/** 餐食计划和购物清单持久化端口。 */
public interface MealPlanRepository {
    boolean sessionOwned(long userId, long sessionId);

    int insertPlan(PlanWrite plan);

    int updatePlanStatus(long userId, long mealPlanId, String status, String validationJson);

    PlanSnapshot findOwnedPlan(long userId, long mealPlanId);

    int insertShoppingList(ShoppingListWrite list);

    ShoppingListSnapshot findOwnedShoppingList(long userId, long mealPlanId);

    record PlanWrite(
            long mealPlanId,
            long userId,
            Long sessionId,
            String planName,
            int days,
            BigDecimal budget,
            String constraintsJson,
            String planJson,
            String validationJson,
            String status) {}

    record PlanSnapshot(
            long mealPlanId,
            long userId,
            Long sessionId,
            String planName,
            int days,
            BigDecimal budget,
            String constraintsJson,
            String planJson,
            String validationJson,
            String status,
            Instant createdAt,
            Instant updatedAt) {}

    record ShoppingListWrite(
            long shoppingListId, long mealPlanId, long userId, String itemsJson, String status) {}

    record ShoppingListSnapshot(
            long shoppingListId,
            long mealPlanId,
            long userId,
            String itemsJson,
            String status,
            Instant createdAt,
            Instant updatedAt) {}
}
