package com.foodmate.application.food;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.foodmate.application.food.port.out.MealPlanRepository;
import com.foodmate.application.food.service.MealPlanService;
import com.foodmate.application.food.service.impl.MealPlanServiceImpl;
import com.foodmate.shared.error.BusinessException;
import com.foodmate.shared.error.ErrorCode;
import com.foodmate.shared.id.IdGenerator;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MealPlanServiceImplTest {
    private static final Instant NOW = Instant.parse("2026-08-12T12:00:00Z");
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void createsDraftAndValidateMovesItToValidated() {
        MealPlanRepository repository = org.mockito.Mockito.mock(MealPlanRepository.class);
        when(repository.insertPlan(any())).thenReturn(1);
        when(repository.updatePlanStatus(eq(7L), eq(100L), eq("validated"), any())).thenReturn(1);
        when(repository.findOwnedPlan(7L, 100L))
                .thenReturn(plan("draft"))
                .thenReturn(plan("validated"));
        MealPlanService service = new MealPlanServiceImpl(repository, ids(100L), mapper);

        MealPlanService.PlanView created = service.create(7L, command(validDaysPlan()));
        MealPlanService.PlanView validated = service.validate(7L, 100L);

        assertEquals("draft", created.status());
        assertEquals("validated", validated.status());
        verify(repository).updatePlanStatus(eq(7L), eq(100L), eq("validated"), any());
    }

    @Test
    void listsOwnedPlansAndPreservesDeletedState() {
        MealPlanRepository repository = org.mockito.Mockito.mock(MealPlanRepository.class);
        when(repository.findOwnedPlans(7L, true)).thenReturn(List.of(plan("saved", 2, false)));
        MealPlanService service = new MealPlanServiceImpl(repository, ids(100L), mapper);

        List<MealPlanService.PlanView> result = service.list(7L);

        assertEquals(1, result.size());
        assertEquals("saved", result.get(0).status());
        assertEquals(2, result.get(0).revision());
        assertEquals(false, result.get(0).deleted());
        verify(repository).findOwnedPlans(7L, true);
    }

    @Test
    void rejectsAllergenAndKeepsPlanOutOfValidatedState() throws Exception {
        MealPlanRepository repository = org.mockito.Mockito.mock(MealPlanRepository.class);
        when(repository.insertPlan(any())).thenReturn(1);
        when(repository.findOwnedPlan(7L, 100L)).thenReturn(plan("draft"));
        MealPlanService service = new MealPlanServiceImpl(repository, ids(100L), mapper);

        service.create(7L, commandWithPlan(List.of("花生"), List.of(), validDaysPlan("花生酱")));

        ArgumentCaptor<MealPlanRepository.PlanWrite> plan =
                ArgumentCaptor.forClass(MealPlanRepository.PlanWrite.class);
        verify(repository).insertPlan(plan.capture());
        assertEquals("draft", plan.getValue().status());
        assertEquals(
                false, mapper.readTree(plan.getValue().validationJson()).get("valid").asBoolean());
        assertEquals(1, mapper.readTree(plan.getValue().validationJson()).get("errors").size());
        verify(repository, never())
                .updatePlanStatus(any(Long.class), any(Long.class), eq("validated"), any());
    }

    @Test
    void saveRequiresValidatedPlan() {
        MealPlanRepository repository = org.mockito.Mockito.mock(MealPlanRepository.class);
        when(repository.findOwnedPlan(7L, 100L)).thenReturn(plan("draft"));
        MealPlanService service = new MealPlanServiceImpl(repository, ids(100L), mapper);

        BusinessException exception =
                assertThrows(BusinessException.class, () -> service.save(7L, 100L));

        assertEquals(ErrorCode.CONFLICT, exception.errorCode());
        verify(repository, never())
                .updatePlanStatus(any(Long.class), any(Long.class), any(), any());
    }

    @Test
    void shoppingListRequiresSavedPlanAndMergesIngredients() {
        MealPlanRepository repository = org.mockito.Mockito.mock(MealPlanRepository.class);
        when(repository.findOwnedPlan(7L, 100L)).thenReturn(plan("saved"));
        when(repository.findOwnedShoppingList(7L, 100L))
                .thenReturn(null)
                .thenReturn(
                        new MealPlanRepository.ShoppingListSnapshot(
                                200L,
                                100L,
                                7L,
                                "[{\"name\":\"鸡蛋\",\"unit\":\"个\",\"amount\":3.000}]",
                                "generated",
                                NOW,
                                NOW));
        when(repository.insertShoppingList(any())).thenReturn(1);
        MealPlanService service = new MealPlanServiceImpl(repository, ids(200L), mapper);

        MealPlanService.ShoppingListView view = service.shoppingList(7L, 100L);

        assertEquals("generated", view.status());
        assertEquals("鸡蛋", view.items().get(0).get("name").asText());
        verify(repository).insertShoppingList(any());
    }

    @Test
    void updateRequiresRevisionAndInvalidatesExistingShoppingList() {
        MealPlanRepository repository = org.mockito.Mockito.mock(MealPlanRepository.class);
        when(repository.findIdempotency(7L, "plan-update-1")).thenReturn(null);
        when(repository.findOwnedPlan(7L, 100L, false))
                .thenReturn(plan("saved", 2, false))
                .thenReturn(plan("draft", 3, false));
        when(repository.reserveAudit(any())).thenReturn(1);
        when(repository.updatePlan(any())).thenReturn(1);
        when(repository.softDeleteShoppingList(7L, 100L)).thenReturn(1);
        MealPlanService service = new MealPlanServiceImpl(repository, ids(300L), mapper);

        MealPlanService.PlanView result =
                service.update(7L, 100L, 2L, updateCommand("plan-update-1"));

        assertEquals("draft", result.status());
        verify(repository).updatePlan(any(MealPlanRepository.UpdatePlanWrite.class));
        verify(repository).softDeleteShoppingList(7L, 100L);
        verify(repository).completeAudit(eq(7L), eq("plan-update-1"), any());
    }

    @Test
    void deleteRejectsStaleRevisionBeforeWriting() {
        MealPlanRepository repository = org.mockito.Mockito.mock(MealPlanRepository.class);
        when(repository.findIdempotency(7L, "plan-delete-1")).thenReturn(null);
        when(repository.findOwnedPlan(7L, 100L, false)).thenReturn(plan("saved", 3, false));
        MealPlanService service = new MealPlanServiceImpl(repository, ids(300L), mapper);

        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () -> service.delete(7L, 100L, 2L, "plan-delete-1"));

        assertEquals(ErrorCode.CONFLICT, exception.errorCode());
        verify(repository, never()).reserveAudit(any());
        verify(repository, never()).softDelete(any(Long.class), any(Long.class), any(Long.class));
    }

    private MealPlanService.CreateCommand command(ArrayNode daysPlan) {
        return commandWithPlan(List.of(), List.of(), daysPlan);
    }

    private MealPlanService.CreateCommand commandWithPlan(
            List<String> allergens, List<String> dislikes, ArrayNode daysPlan) {
        return new MealPlanService.CreateCommand(
                null,
                "一周计划",
                2,
                1,
                new BigDecimal("300.00"),
                2000,
                100,
                allergens,
                dislikes,
                daysPlan);
    }

    private MealPlanService.UpdateCommand updateCommand(String idempotencyKey) {
        return new MealPlanService.UpdateCommand(
                "更新计划",
                2,
                1,
                new BigDecimal("300.00"),
                2000,
                100,
                List.of(),
                List.of(),
                validDaysPlan(),
                idempotencyKey);
    }

    private MealPlanRepository.PlanSnapshot plan(String status) {
        return plan(status, 1, false);
    }

    private MealPlanRepository.PlanSnapshot plan(String status, long revision, boolean deleted) {
        try {
            return new MealPlanRepository.PlanSnapshot(
                    100L,
                    7L,
                    null,
                    "一周计划",
                    1,
                    new BigDecimal("300.00"),
                    mapper.writeValueAsString(
                            mapper.readTree(
                                    "{\"people\":2,\"calorie_target\":2000,\"protein_target\":100,\"allergens\":[],\"dislikes\":[]}")),
                    mapper.writeValueAsString(validDaysPlan()),
                    "{\"valid\":true,\"errors\":[],\"warnings\":[]}",
                    status,
                    null,
                    revision,
                    deleted,
                    NOW,
                    NOW);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private ArrayNode validDaysPlan(String... ingredientNames) {
        String ingredient = ingredientNames.length == 0 ? "鸡蛋" : ingredientNames[0];
        ArrayNode days = JsonNodeFactory.instance.arrayNode();
        ObjectNode day = JsonNodeFactory.instance.objectNode();
        for (String meal : List.of("breakfast", "lunch", "dinner")) {
            ObjectNode mealNode = day.putObject(meal);
            ArrayNode ingredients = mealNode.putArray("ingredients");
            ingredients.addObject().put("name", ingredient).put("amount", 1).put("unit", "个");
        }
        days.add(day);
        return days;
    }

    private IdGenerator ids(long... values) {
        return new IdGenerator() {
            private int index;

            @Override
            public long nextId() {
                return values[Math.min(index++, values.length - 1)];
            }
        };
    }
}
