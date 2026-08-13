package com.foodmate.application.food.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** 提供当天、最近 7 天和最近 30 天的确定性营养分析。 */
public interface NutritionAnalysisService {
    Analysis analyze(long userId, String range);

    record Analysis(
            String range,
            Instant from,
            Instant to,
            int totalItems,
            int matchedItems,
            BigDecimal coverage,
            BigDecimal caloriesKcal,
            BigDecimal proteinG,
            BigDecimal fatG,
            BigDecimal carbsG,
            Integer calorieTarget,
            Integer proteinTarget,
            boolean incomplete,
            List<String> unmatchedNames,
            String disclaimer) {
        public Analysis {
            unmatchedNames = unmatchedNames == null ? List.of() : List.copyOf(unmatchedNames);
        }
    }
}
