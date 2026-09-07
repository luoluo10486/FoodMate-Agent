package com.foodmate.application.food;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.foodmate.application.common.service.OperationAuditService;
import com.foodmate.application.food.port.out.CompositeDishRepository;
import com.foodmate.application.food.port.out.FoodLogRepository;
import com.foodmate.application.food.service.FoodLogService;
import com.foodmate.application.food.service.impl.FoodLogServiceImpl;
import com.foodmate.shared.food.enums.MealType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class FoodLogCompositeDishTest {
    @Test
    void recordsDishServingsAndNutritionSnapshotWithoutRequiringRawItems() {
        FoodLogRepository foods = Mockito.mock(FoodLogRepository.class);
        CompositeDishRepository dishes = Mockito.mock(CompositeDishRepository.class);
        OperationAuditService audit = Mockito.mock(OperationAuditService.class);
        when(foods.findIdempotency(7L, "log-1")).thenReturn(null);
        when(audit.reserve(
                        anyLong(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyMap()))
                .thenReturn(1);
        CompositeDishRepository.DishSnapshot dish =
                new CompositeDishRepository.DishSnapshot(
                        100L,
                        7L,
                        "鸡肉饭",
                        new BigDecimal("2"),
                        new BigDecimal("165.0000"),
                        new BigDecimal("31.0000"),
                        new BigDecimal("3.6000"),
                        BigDecimal.ZERO.setScale(4),
                        "USDA:2025",
                        3L,
                        false,
                        Instant.now(),
                        Instant.now(),
                        List.of());
        when(dishes.findOwned(7L, 100L, false)).thenReturn(dish);
        when(foods.insertFoodLog(any())).thenReturn(1);
        when(foods.findOwned(7L, 200L, false))
                .thenReturn(
                        new FoodLogRepository.FoodLogSnapshot(
                                200L,
                                7L,
                                null,
                                null,
                                Instant.parse("2026-09-07T08:00:00Z"),
                                "breakfast",
                                null,
                                "manual",
                                1L,
                                false,
                                Instant.now(),
                                Instant.now(),
                                100L,
                                3L,
                                new BigDecimal("0.5"),
                                "{\"dish_id\":100}",
                                List.of()));
        AtomicLong nextId = new AtomicLong(199);
        FoodLogService service =
                new FoodLogServiceImpl(foods, nextId::incrementAndGet, audit, dishes);

        service.create(
                7L,
                new FoodLogService.CreateCommand(
                        null,
                        null,
                        Instant.parse("2026-09-07T08:00:00Z"),
                        MealType.BREAKFAST,
                        null,
                        "log-1",
                        "manual",
                        100L,
                        3L,
                        new BigDecimal("0.5"),
                        List.of()));

        ArgumentCaptor<FoodLogRepository.FoodLogWrite> log =
                ArgumentCaptor.forClass(FoodLogRepository.FoodLogWrite.class);
        verify(foods).insertFoodLog(log.capture());
        assertEquals(100L, log.getValue().compositeDishId());
        assertEquals(3L, log.getValue().compositeDishRevision());
        assertEquals(new BigDecimal("0.5"), log.getValue().compositeDishServings());
        verify(foods).insertItem(any(FoodLogRepository.FoodLogItemWrite.class));
    }
}
