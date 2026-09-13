package com.foodmate.api.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodmate.api.advice.GlobalExceptionHandler;
import com.foodmate.api.controller.food.MealPlanController;
import com.foodmate.api.filter.TraceContextFilter;
import com.foodmate.application.account.service.UserAccountService;
import com.foodmate.application.food.service.MealPlanService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class MealPlanControllerTest {
    private UserAccountService accounts;
    private MealPlanService plans;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        accounts = mock(UserAccountService.class);
        plans = mock(MealPlanService.class);
        mvc =
                MockMvcBuilders.standaloneSetup(new MealPlanController(accounts, plans))
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .addFilters(new TraceContextFilter())
                        .build();
    }

    @Test
    void readsPlanWithRevisionAndDeletedState() throws Exception {
        when(accounts.requireSessionUser("session-1")).thenReturn(user());
        when(plans.get(7L, 100L)).thenReturn(view());

        mvc.perform(
                        get("/api/meal-plans/100")
                                .cookie(
                                        new jakarta.servlet.http.Cookie(
                                                "foodmate_session", "session-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.meal_plan_id", is("100")))
                .andExpect(jsonPath("$.data.revision", is(2)))
                .andExpect(jsonPath("$.data.deleted", is(false)));
    }

    @Test
    void listsOwnedPlansIncludingArchivedRecords() throws Exception {
        when(accounts.requireSessionUser("session-1")).thenReturn(user());
        when(plans.list(7L)).thenReturn(List.of(view()));

        mvc.perform(
                        get("/api/meal-plans")
                                .cookie(
                                        new jakarta.servlet.http.Cookie(
                                                "foodmate_session", "session-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].meal_plan_id", is("100")))
                .andExpect(jsonPath("$.data[0].revision", is(2)));

        verify(plans).list(7L);
    }

    @Test
    void updatesPlanWithRevisionAndIdempotencyKey() throws Exception {
        when(accounts.requireSessionUser("session-1")).thenReturn(user());
        when(plans.update(eq(7L), eq(100L), eq(2L), any(MealPlanService.UpdateCommand.class)))
                .thenReturn(view());

        mvc.perform(
                        patch("/api/meal-plans/100")
                                .param("revision", "2")
                                .header("Idempotency-Key", "plan-update-1")
                                .cookie(
                                        new jakarta.servlet.http.Cookie(
                                                "foodmate_session", "session-1"))
                                .contentType("application/json")
                                .content(
                                        "{\"plan_name\":\"午餐计划\",\"people\":2,\"days\":1,"
                                                + "\"budget\":300,\"days_plan\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.revision", is(2)));

        verify(plans).update(eq(7L), eq(100L), eq(2L), any(MealPlanService.UpdateCommand.class));
    }

    @Test
    void deletesPlanWithRevisionAndIdempotencyKey() throws Exception {
        when(accounts.requireSessionUser("session-1")).thenReturn(user());

        mvc.perform(
                        delete("/api/meal-plans/100")
                                .param("revision", "2")
                                .header("Idempotency-Key", "plan-delete-1")
                                .cookie(
                                        new jakarta.servlet.http.Cookie(
                                                "foodmate_session", "session-1")))
                .andExpect(status().isOk());

        verify(plans).delete(7L, 100L, 2L, "plan-delete-1");
    }

    @Test
    void createsPlanWithRequestAndIdempotencyKey() throws Exception {
        when(accounts.requireSessionUser("session-1")).thenReturn(user());
        when(plans.create(eq(7L), any(MealPlanService.CreateCommand.class))).thenReturn(view());

        mvc.perform(
                        post("/api/meal-plans")
                                .cookie(
                                        new jakarta.servlet.http.Cookie(
                                                "foodmate_session", "session-1"))
                                .header("Idempotency-Key", "plan-create-1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"plan_name\":\"午餐计划\",\"people\":2,\"days\":1,"
                                                + "\"budget\":300,\"days_plan\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.meal_plan_id", is("100")));

        verify(plans).create(eq(7L), any(MealPlanService.CreateCommand.class));
    }

    @Test
    void validatesSavesAndRestoresPlanWithRevision() throws Exception {
        when(accounts.requireSessionUser("session-1")).thenReturn(user());
        when(plans.validate(7L, 100L, 2L, "plan-validate-1")).thenReturn(view());
        when(plans.save(7L, 100L, 3L, "plan-save-1")).thenReturn(view());
        when(plans.restore(7L, 100L, 4L, "plan-restore-1")).thenReturn(view());

        mvc.perform(
                        post("/api/meal-plans/100/validate")
                                .param("revision", "2")
                                .header("Idempotency-Key", "plan-validate-1")
                                .cookie(
                                        new jakarta.servlet.http.Cookie(
                                                "foodmate_session", "session-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.meal_plan_id", is("100")));

        mvc.perform(
                        post("/api/meal-plans/100/save")
                                .param("revision", "3")
                                .header("Idempotency-Key", "plan-save-1")
                                .cookie(
                                        new jakarta.servlet.http.Cookie(
                                                "foodmate_session", "session-1")))
                .andExpect(status().isOk());

        mvc.perform(
                        post("/api/meal-plans/100/restore")
                                .param("revision", "4")
                                .header("Idempotency-Key", "plan-restore-1")
                                .cookie(
                                        new jakarta.servlet.http.Cookie(
                                                "foodmate_session", "session-1")))
                .andExpect(status().isOk());

        verify(plans).validate(7L, 100L, 2L, "plan-validate-1");
        verify(plans).save(7L, 100L, 3L, "plan-save-1");
        verify(plans).restore(7L, 100L, 4L, "plan-restore-1");
    }

    @Test
    void readsAndCreatesShoppingListFromOwnedPlan() throws Exception {
        when(accounts.requireSessionUser("session-1")).thenReturn(user());
        when(plans.shoppingList(7L, 100L)).thenReturn(shoppingList());

        mvc.perform(
                        post("/api/meal-plans/100/shopping-list")
                                .cookie(
                                        new jakarta.servlet.http.Cookie(
                                                "foodmate_session", "session-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shopping_list_id", is("500")))
                .andExpect(jsonPath("$.data.items[0].name", is("鸡胸肉")));

        mvc.perform(
                        get("/api/meal-plans/100/shopping-list")
                                .cookie(
                                        new jakarta.servlet.http.Cookie(
                                                "foodmate_session", "session-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.meal_plan_id", is("100")));

        verify(plans, times(2)).shoppingList(7L, 100L);
    }

    @Test
    void readsProgressAndUpdatesShoppingItem() throws Exception {
        when(accounts.requireSessionUser("session-1")).thenReturn(user());
        when(plans.progress(7L, 100L))
                .thenReturn(
                        new MealPlanService.ProgressView(
                                100L, 3, 1, new BigDecimal("0.3333"), List.of()));
        when(plans.setShoppingItemPurchased(7L, 100L, 501L, true, "shopping-update-1"))
                .thenReturn(shoppingList());

        mvc.perform(
                        get("/api/meal-plans/100/progress")
                                .cookie(
                                        new jakarta.servlet.http.Cookie(
                                                "foodmate_session", "session-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mealPlanId", is(100)))
                .andExpect(jsonPath("$.data.completedMealCount", is(1)));

        mvc.perform(
                        patch("/api/meal-plans/100/shopping-list/items/501")
                                .header("Idempotency-Key", "shopping-update-1")
                                .cookie(
                                        new jakarta.servlet.http.Cookie(
                                                "foodmate_session", "session-1"))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"purchased\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.shopping_list_id", is("500")));

        verify(plans).progress(7L, 100L);
        verify(plans).setShoppingItemPurchased(7L, 100L, 501L, true, "shopping-update-1");
    }

    private UserAccountService.UserRecord user() {
        return new UserAccountService.UserRecord(
                7L, "user", "user@example.com", "hash", "user", "User", "active");
    }

    private MealPlanService.PlanView view() {
        return new MealPlanService.PlanView(
                100L,
                null,
                "午餐计划",
                2,
                1,
                new BigDecimal("300.00"),
                new ObjectMapperFactory().object("people", 2),
                new ObjectMapperFactory().array(),
                new ObjectMapperFactory().object("valid", true),
                "draft",
                2,
                false,
                Instant.parse("2026-08-12T12:00:00Z"),
                Instant.parse("2026-08-12T12:00:00Z"));
    }

    private MealPlanService.ShoppingListView shoppingList() {
        var items = new ObjectMapperFactory().array();
        items.addObject().put("shopping_list_item_id", "501").put("name", "鸡胸肉");
        return new MealPlanService.ShoppingListView(
                500L,
                100L,
                items,
                "generated",
                Instant.parse("2026-08-12T12:00:00Z"),
                Instant.parse("2026-08-12T12:00:00Z"));
    }

    private static final class ObjectMapperFactory {
        private final com.fasterxml.jackson.databind.ObjectMapper mapper =
                new com.fasterxml.jackson.databind.ObjectMapper();

        com.fasterxml.jackson.databind.node.ObjectNode object(String name, int value) {
            return mapper.createObjectNode().put(name, value);
        }

        com.fasterxml.jackson.databind.node.ObjectNode object(String name, boolean value) {
            return mapper.createObjectNode().put(name, value);
        }

        com.fasterxml.jackson.databind.node.ArrayNode array() {
            return mapper.createArrayNode();
        }
    }
}
