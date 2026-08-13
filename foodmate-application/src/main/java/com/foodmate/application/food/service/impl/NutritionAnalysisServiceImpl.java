package com.foodmate.application.food.service.impl;

import com.foodmate.application.food.port.out.NutritionAnalysisRepository;
import com.foodmate.application.food.service.NutritionAnalysisService;
import com.foodmate.shared.error.BusinessException;
import com.foodmate.shared.error.ErrorCode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Locale;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/** 营养分析应用服务；只使用数据库中已审核目录产生的冻结快照。 */
@Service
@Profile("local")
public class NutritionAnalysisServiceImpl implements NutritionAnalysisService {
    private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");
    private static final String DISCLAIMER = "仅用于饮食记录参考，不构成医疗诊断或治疗建议";

    private final NutritionAnalysisRepository store;

    public NutritionAnalysisServiceImpl(NutritionAnalysisRepository store) {
        this.store = store;
    }

    @Override
    public Analysis analyze(long userId, String range) {
        Window window = window(range);
        NutritionAnalysisRepository.NutrientAggregate aggregate =
                store.aggregate(userId, window.from(), window.to());
        NutritionAnalysisRepository.Targets targets = store.findTargets(userId);
        int total = aggregate.totalItems();
        BigDecimal coverage =
                total == 0
                        ? BigDecimal.ZERO.setScale(4)
                        : BigDecimal.valueOf(aggregate.matchedItems())
                                .divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP);
        return new Analysis(
                window.name(),
                window.from(),
                window.to(),
                total,
                aggregate.matchedItems(),
                coverage,
                money(aggregate.caloriesKcal()),
                nutrient(aggregate.proteinG()),
                nutrient(aggregate.fatG()),
                nutrient(aggregate.carbsG()),
                targets == null ? null : targets.calorieTarget(),
                targets == null ? null : targets.proteinTarget(),
                aggregate.matchedItems() < total,
                store.unmatchedNames(userId, window.from(), window.to()),
                DISCLAIMER);
    }

    private Window window(String rawRange) {
        String value = rawRange == null ? "today" : rawRange.trim().toLowerCase(Locale.ROOT);
        Instant now = Instant.now();
        return switch (value) {
            case "today" -> {
                LocalDate date = now.atZone(ZoneOffset.UTC).toLocalDate();
                yield new Window(
                        "today",
                        date.atStartOfDay().toInstant(ZoneOffset.UTC),
                        date.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC));
            }
            case "7d", "last_7_days" -> new Window("7d", now.minusSeconds(7 * 86400L), now);
            case "30d", "last_30_days" -> new Window("30d", now.minusSeconds(30 * 86400L), now);
            default ->
                    throw new BusinessException(
                            ErrorCode.INVALID_ARGUMENT, "分析范围必须是 today、7d 或 30d");
        };
    }

    private static BigDecimal money(BigDecimal value) {
        return value == null
                ? BigDecimal.ZERO.setScale(2)
                : value.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal nutrient(BigDecimal value) {
        return value == null
                ? BigDecimal.ZERO.setScale(4)
                : value.setScale(4, RoundingMode.HALF_UP);
    }

    private record Window(String name, Instant from, Instant to) {}
}
