package com.foodmate.application.food.port.out;

import java.math.BigDecimal;
import java.time.Instant;

/** 餐食计划和购物清单持久化端口。 */
public interface MealPlanRepository {
    boolean sessionOwned(long userId, long sessionId);

    IdempotencyRecord findIdempotency(long userId, String idempotencyKey);

    int insertPlan(PlanWrite plan);

    int updatePlanStatus(long userId, long mealPlanId, String status, String validationJson);

    int updatePlanStatus(
            long userId,
            long mealPlanId,
            long expectedRevision,
            String status,
            String validationJson);

    int updatePlan(UpdatePlanWrite plan);

    PlanSnapshot findOwnedPlan(long userId, long mealPlanId);

    PlanSnapshot findOwnedPlan(long userId, long mealPlanId, boolean includeDeleted);

    int softDelete(long userId, long mealPlanId, long revision);

    int restore(long userId, long mealPlanId, long revision);

    int softDeleteShoppingList(long userId, long mealPlanId);

    int reserveAudit(AuditWrite audit);

    int completeAudit(long operatorId, String idempotencyKey, String responseJson);

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
            String status,
            String idempotencyKey,
            long revision) {
        public PlanWrite(
                long mealPlanId,
                long userId,
                Long sessionId,
                String planName,
                int days,
                BigDecimal budget,
                String constraintsJson,
                String planJson,
                String validationJson,
                String status) {
            this(
                    mealPlanId,
                    userId,
                    sessionId,
                    planName,
                    days,
                    budget,
                    constraintsJson,
                    planJson,
                    validationJson,
                    status,
                    null,
                    1);
        }
    }

    record UpdatePlanWrite(
            long userId,
            long mealPlanId,
            long expectedRevision,
            String planName,
            int days,
            BigDecimal budget,
            String constraintsJson,
            String planJson,
            String validationJson) {}

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
            String idempotencyKey,
            long revision,
            boolean deleted,
            Instant createdAt,
            Instant updatedAt) {
        public PlanSnapshot(
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
                Instant updatedAt) {
            this(
                    mealPlanId,
                    userId,
                    sessionId,
                    planName,
                    days,
                    budget,
                    constraintsJson,
                    planJson,
                    validationJson,
                    status,
                    null,
                    1,
                    false,
                    createdAt,
                    updatedAt);
        }
    }

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

    record IdempotencyRecord(String parametersDigest, String result, String responseJson) {}

    record AuditWrite(
            long operationAuditId,
            long operatorId,
            String requestId,
            String traceId,
            String targetType,
            String targetId,
            String action,
            String parametersDigest,
            String idempotencyKey,
            String responseJson) {}
}
