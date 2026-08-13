package com.foodmate.application.food;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.foodmate.application.food.port.out.NutritionAnalysisRepository;
import com.foodmate.application.food.service.NutritionAnalysisService;
import com.foodmate.application.food.service.impl.NutritionAnalysisServiceImpl;
import com.foodmate.shared.error.BusinessException;
import com.foodmate.shared.error.ErrorCode;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

class NutritionAnalysisServiceImplTest {
    @Test
    void returnsCoverageTargetsAndIncompleteNames() {
        NutritionAnalysisRepository repository = mock(NutritionAnalysisRepository.class);
        when(repository.aggregate(
                        ArgumentMatchers.eq(7L), ArgumentMatchers.any(), ArgumentMatchers.any()))
                .thenReturn(
                        new NutritionAnalysisRepository.NutrientAggregate(
                                4,
                                3,
                                new BigDecimal("123.4567"),
                                new BigDecimal("22.34567"),
                                new BigDecimal("8.1"),
                                new BigDecimal("18.2")));
        when(repository.unmatchedNames(
                        ArgumentMatchers.eq(7L), ArgumentMatchers.any(), ArgumentMatchers.any()))
                .thenReturn(List.of("unknown food"));
        when(repository.findTargets(7L))
                .thenReturn(new NutritionAnalysisRepository.Targets(1800, 120));

        NutritionAnalysisService.Analysis result =
                new NutritionAnalysisServiceImpl(repository).analyze(7L, "7d");

        assertEquals("7d", result.range());
        assertEquals(4, result.totalItems());
        assertEquals(3, result.matchedItems());
        assertEquals(new BigDecimal("0.7500"), result.coverage());
        assertEquals(new BigDecimal("123.46"), result.caloriesKcal());
        assertEquals(new BigDecimal("22.3457"), result.proteinG());
        assertEquals(1800, result.calorieTarget());
        assertEquals(120, result.proteinTarget());
        assertEquals(true, result.incomplete());
        assertEquals(List.of("unknown food"), result.unmatchedNames());
        verify(repository).findTargets(7L);
    }

    @Test
    void rejectsUnsupportedRange() {
        NutritionAnalysisRepository repository = mock(NutritionAnalysisRepository.class);

        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () -> new NutritionAnalysisServiceImpl(repository).analyze(7L, "90d"));

        assertEquals(ErrorCode.INVALID_ARGUMENT, exception.errorCode());
    }
}
