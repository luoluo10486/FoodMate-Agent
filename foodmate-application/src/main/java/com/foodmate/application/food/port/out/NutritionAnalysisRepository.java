package com.foodmate.application.food.port.out;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** 营养分析查询端口；由 infrastructure 负责 SQL 聚合和用户目标读取。 */
public interface NutritionAnalysisRepository {
    NutrientAggregate aggregate(long userId, Instant from, Instant to);

    List<String> unmatchedNames(long userId, Instant from, Instant to);

    Targets findTargets(long userId);

    record NutrientAggregate(
            int totalItems,
            int matchedItems,
            BigDecimal caloriesKcal,
            BigDecimal proteinG,
            BigDecimal fatG,
            BigDecimal carbsG) {}

    record Targets(Integer calorieTarget, Integer proteinTarget) {}
}
