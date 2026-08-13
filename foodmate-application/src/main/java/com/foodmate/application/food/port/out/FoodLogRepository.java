package com.foodmate.application.food.port.out;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** 饮食记录持久化端口；SQL 细节由 infrastructure 实现。 */
public interface FoodLogRepository {
    boolean sessionOwned(long userId, long sessionId);

    boolean agentRunOwned(long userId, long agentRunId);

    NutritionFoodLookup findNutritionFood(String normalizedName);

    UnitConversionLookup findUnitConversion(
            long nutritionFoodId, String sourceUnit, String targetUnit);

    IdempotencyRecord findIdempotency(long userId, String idempotencyKey);

    int insertFoodLog(FoodLogWrite write);

    int updateFoodLog(UpdateFoodLogWrite write);

    int softDeleteItems(long userId, long foodLogId);

    void insertItem(FoodLogItemWrite item);

    List<FoodLogSnapshot> findVisible(long userId, Instant from, Instant to);

    FoodLogSnapshot findOwned(long userId, long foodLogId, boolean includeDeleted);

    int softDelete(long userId, long foodLogId, long revision);

    int restore(long userId, long foodLogId, long revision);

    int reserveAudit(AuditWrite audit);

    int completeAudit(long operatorId, String idempotencyKey, String responseJson);

    record FoodLogWrite(
            long foodLogId,
            long userId,
            Long sessionId,
            Long agentRunId,
            Instant mealTime,
            String mealType,
            String notes,
            String source,
            String idempotencyKey,
            long revision) {}

    record UpdateFoodLogWrite(
            long userId,
            long foodLogId,
            long expectedRevision,
            Instant mealTime,
            String mealType,
            String notes) {}

    record FoodLogItemWrite(
            long foodLogItemId,
            long foodLogId,
            int itemOrder,
            String rawName,
            BigDecimal amount,
            String unit,
            long userId,
            Long nutritionFoodId,
            BigDecimal normalizedAmount,
            String normalizedUnit,
            Long conversionId,
            BigDecimal caloriesKcal,
            BigDecimal proteinG,
            BigDecimal fatG,
            BigDecimal carbsG,
            String nutritionStatus,
            String nutritionSource,
            String nutritionVersion) {
        public FoodLogItemWrite(
                long foodLogItemId,
                long foodLogId,
                int itemOrder,
                String rawName,
                BigDecimal amount,
                String unit,
                long userId) {
            this(
                    foodLogItemId,
                    foodLogId,
                    itemOrder,
                    rawName,
                    amount,
                    unit,
                    userId,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    "pending",
                    null,
                    null);
        }
    }

    record NutritionFoodLookup(
            long nutritionFoodId,
            String standardName,
            String basisUnit,
            BigDecimal caloriesKcalPer100,
            BigDecimal proteinGPer100,
            BigDecimal fatGPer100,
            BigDecimal carbsGPer100,
            String sourceName,
            String sourceVersion) {}

    record UnitConversionLookup(
            long conversionId,
            BigDecimal multiplier,
            String targetUnit,
            String sourceName,
            String sourceVersion) {}

    record FoodLogSnapshot(
            long foodLogId,
            long userId,
            Long sessionId,
            Long agentRunId,
            Instant mealTime,
            String mealType,
            String notes,
            String source,
            long revision,
            boolean deleted,
            Instant createdAt,
            Instant updatedAt,
            List<FoodLogItemSnapshot> items) {}

    record FoodLogItemSnapshot(
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
