package com.foodmate.application.food;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodmate.application.common.service.OperationAuditService;
import com.foodmate.application.food.port.out.CompositeDishRepository;
import com.foodmate.application.food.port.out.FoodLogRepository;
import com.foodmate.application.food.service.CompositeDishService;
import com.foodmate.application.food.service.impl.CompositeDishServiceImpl;
import com.foodmate.shared.id.IdGenerator;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class CompositeDishServiceImplTest {
    @Test
    void calculatesPerServingNutritionFromAuthoritativeFoodValues() {
        CompositeDishRepository dishes = Mockito.mock(CompositeDishRepository.class);
        FoodLogRepository foods = Mockito.mock(FoodLogRepository.class);
        OperationAuditService audit = Mockito.mock(OperationAuditService.class);
        when(dishes.findIdempotency(7L, "dish-1")).thenReturn(null);
        when(audit.reserve(
                        anyLong(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyMap()))
                .thenReturn(1);
        when(dishes.insertDish(any())).thenReturn(1);
        when(foods.findNutritionFoodById(171477L))
                .thenReturn(
                        new FoodLogRepository.NutritionFoodLookup(
                                171477L,
                                "Chicken breast",
                                "g",
                                new BigDecimal("165"),
                                new BigDecimal("31"),
                                new BigDecimal("3.6"),
                                BigDecimal.ZERO,
                                "USDA",
                                "2025"));
        when(dishes.findOwned(7L, 100L, false))
                .thenReturn(
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
                                1L,
                                false,
                                Instant.now(),
                                Instant.now(),
                                List.of()));

        AtomicLong nextId = new AtomicLong(99);
        IdGenerator ids = nextId::incrementAndGet;
        CompositeDishService service =
                new CompositeDishServiceImpl(dishes, foods, ids, new ObjectMapper(), audit);

        CompositeDishService.CompositeDishView result =
                service.create(
                        7L,
                        new CompositeDishService.CreateCommand(
                                "鸡肉饭",
                                new BigDecimal("2"),
                                List.of(
                                        new CompositeDishService.ComponentCommand(
                                                171477L, "熟鸡胸肉", new BigDecimal("200"), "g")),
                                "dish-1"));

        assertEquals(new BigDecimal("165.0000"), result.caloriesKcalPerServing());
        assertEquals(new BigDecimal("31.0000"), result.proteinGPerServing());
        ArgumentCaptor<CompositeDishRepository.ItemWrite> item =
                ArgumentCaptor.forClass(CompositeDishRepository.ItemWrite.class);
        verify(dishes).insertItem(item.capture());
        assertEquals(100L, item.getValue().compositeDishId());
        assertEquals(7L, item.getValue().userId());
        verify(audit).complete(7L, "dish-1", "{\"resource_id\":100,\"revision\":1}");
    }
}
