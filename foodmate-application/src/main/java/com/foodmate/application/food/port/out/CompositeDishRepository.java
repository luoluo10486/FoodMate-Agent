package com.foodmate.application.food.port.out;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** 用户复合菜持久化端口；复合菜不混入公共营养原子目录。 */
public interface CompositeDishRepository {
    IdempotencyRecord findIdempotency(long userId, String idempotencyKey);

    int insertDish(DishWrite write);

    int updateDish(UpdateDishWrite write);

    void insertItem(ItemWrite write);

    int softDeleteItems(long userId, long compositeDishId);

    DishSnapshot findOwned(long userId, long compositeDishId, boolean includeDeleted);

    List<DishSnapshot> findOwnedList(long userId);

    int softDelete(long userId, long compositeDishId, long revision);

    record DishWrite(
            long compositeDishId,
            long userId,
            String dishName,
            BigDecimal totalServings,
            BigDecimal caloriesKcalPerServing,
            BigDecimal proteinGPerServing,
            BigDecimal fatGPerServing,
            BigDecimal carbsGPerServing,
            String nutritionSource,
            String idempotencyKey,
            long revision) {}

    record UpdateDishWrite(
            long userId,
            long compositeDishId,
            long expectedRevision,
            String dishName,
            BigDecimal totalServings,
            BigDecimal caloriesKcalPerServing,
            BigDecimal proteinGPerServing,
            BigDecimal fatGPerServing,
            BigDecimal carbsGPerServing,
            String nutritionSource) {}

    record ItemWrite(
            long itemId,
            long compositeDishId,
            int itemOrder,
            long nutritionFoodId,
            String rawName,
            BigDecimal amount,
            String unit,
            BigDecimal normalizedAmount,
            String normalizedUnit,
            Long conversionId,
            BigDecimal caloriesKcal,
            BigDecimal proteinG,
            BigDecimal fatG,
            BigDecimal carbsG,
            long userId) {}

    record DishSnapshot(
            long compositeDishId,
            long userId,
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
            List<ItemSnapshot> items) {}

    record ItemSnapshot(
            long itemId,
            int itemOrder,
            long nutritionFoodId,
            String rawName,
            BigDecimal amount,
            String unit,
            BigDecimal normalizedAmount,
            String normalizedUnit,
            Long conversionId,
            BigDecimal caloriesKcal,
            BigDecimal proteinG,
            BigDecimal fatG,
            BigDecimal carbsG) {}

    record IdempotencyRecord(String parametersDigest, String result, String responseJson) {}
}
