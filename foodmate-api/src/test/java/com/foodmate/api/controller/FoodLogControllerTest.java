package com.foodmate.api.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodmate.api.advice.GlobalExceptionHandler;
import com.foodmate.api.controller.food.FoodLogController;
import com.foodmate.api.filter.TraceContextFilter;
import com.foodmate.application.account.service.UserAccountService;
import com.foodmate.application.food.service.FoodLogService;
import com.foodmate.shared.food.enums.MealType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class FoodLogControllerTest {
    private UserAccountService accounts;
    private FoodLogService foods;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        accounts = mock(UserAccountService.class);
        foods = mock(FoodLogService.class);
        mvc =
                MockMvcBuilders.standaloneSetup(new FoodLogController(accounts, foods))
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .addFilters(new TraceContextFilter())
                        .build();
    }

    @Test
    void createsFoodLogWithAuthenticatedUserAndIdempotencyKey() throws Exception {
        when(accounts.requireSessionUser("session-1")).thenReturn(user(7L));
        when(foods.create(eq(7L), any(FoodLogService.CreateCommand.class))).thenReturn(view());

        mvc.perform(
                        post("/api/food-logs")
                                .cookie(
                                        new jakarta.servlet.http.Cookie(
                                                "foodmate_session", "session-1"))
                                .header("Idempotency-Key", "create-1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"meal_time\":\"2026-08-12T12:00:00Z\",\"meal_type\":\"lunch\",\"items\":[{\"raw_name\":\"rice\",\"amount\":100,\"unit\":\"g\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.food_log_id", is("100")))
                .andExpect(jsonPath("$.data.items[0].nutrition_status", is("pending")));

        verify(foods).create(eq(7L), any(FoodLogService.CreateCommand.class));
    }

    @Test
    void updatesFoodLogWithRevisionAndIdempotencyKey() throws Exception {
        when(accounts.requireSessionUser("session-1")).thenReturn(user(7L));
        when(foods.update(eq(7L), eq(100L), eq(1L), any(FoodLogService.UpdateCommand.class)))
                .thenReturn(view());

        mvc.perform(
                        patch("/api/food-logs/100")
                                .cookie(
                                        new jakarta.servlet.http.Cookie(
                                                "foodmate_session", "session-1"))
                                .param("revision", "1")
                                .header("Idempotency-Key", "update-1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"meal_time\":\"2026-08-12T12:00:00Z\",\"meal_type\":\"lunch\",\"notes\":\"updated\",\"items\":[{\"raw_name\":\"rice\",\"amount\":100,\"unit\":\"g\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.revision", is(1)));

        verify(foods).update(eq(7L), eq(100L), eq(1L), any(FoodLogService.UpdateCommand.class));
    }

    @Test
    void listsDeletedFoodLogsForAuthenticatedUser() throws Exception {
        when(accounts.requireSessionUser("session-1")).thenReturn(user(7L));
        when(foods.listDeleted(7L)).thenReturn(List.of(view()));

        mvc.perform(
                        get("/api/food-logs/deleted")
                                .cookie(
                                        new jakarta.servlet.http.Cookie(
                                                "foodmate_session", "session-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].food_log_id", is("100")));

        verify(foods).listDeleted(7L);
    }

    private UserAccountService.UserRecord user(long id) {
        return new UserAccountService.UserRecord(
                id, "user", "user@example.com", "hash", "user", "User", "active");
    }

    private FoodLogService.FoodLogView view() {
        return new FoodLogService.FoodLogView(
                100L,
                null,
                null,
                Instant.parse("2026-08-12T12:00:00Z"),
                MealType.LUNCH,
                null,
                "manual",
                1L,
                false,
                Instant.parse("2026-08-12T12:00:00Z"),
                Instant.parse("2026-08-12T12:00:00Z"),
                List.of(
                        new FoodLogService.ItemView(
                                101L,
                                0,
                                "rice",
                                new BigDecimal("100"),
                                "g",
                                "pending",
                                null,
                                null,
                                null,
                                null)));
    }
}
