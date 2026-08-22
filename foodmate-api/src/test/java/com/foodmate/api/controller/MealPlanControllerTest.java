package com.foodmate.api.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
