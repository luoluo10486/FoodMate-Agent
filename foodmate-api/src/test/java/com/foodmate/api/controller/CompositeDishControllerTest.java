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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodmate.api.advice.GlobalExceptionHandler;
import com.foodmate.api.controller.food.CompositeDishController;
import com.foodmate.api.filter.TraceContextFilter;
import com.foodmate.application.account.service.UserAccountService;
import com.foodmate.application.food.service.CompositeDishService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class CompositeDishControllerTest {
    private UserAccountService accounts;
    private CompositeDishService dishes;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        accounts = mock(UserAccountService.class);
        dishes = mock(CompositeDishService.class);
        mvc =
                MockMvcBuilders.standaloneSetup(new CompositeDishController(accounts, dishes))
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .addFilters(new TraceContextFilter())
                        .build();
    }

    @Test
    void createsCompositeDishWithAuthenticatedUserAndIdempotencyKey() throws Exception {
        when(accounts.requireSessionUser("session-1")).thenReturn(user());
        when(dishes.create(eq(7L), any(CompositeDishService.CreateCommand.class)))
                .thenReturn(view());

        mvc.perform(
                        post("/api/composite-dishes")
                                .cookie(
                                        new jakarta.servlet.http.Cookie(
                                                "foodmate_session", "session-1"))
                                .header("Idempotency-Key", "create-1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"dish_name\":\"鸡肉饭\",\"total_servings\":2,\"components\":[{\"nutrition_food_id\":171477,\"raw_name\":\"熟鸡胸肉\",\"amount\":300,\"unit\":\"g\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.composite_dish_id", is("100")))
                .andExpect(jsonPath("$.data.components[0].nutrition_food_id", is("171477")));

        verify(dishes).create(eq(7L), any(CompositeDishService.CreateCommand.class));
    }

    @Test
    void listsAndReadsOwnedCompositeDishes() throws Exception {
        when(accounts.requireSessionUser("session-1")).thenReturn(user());
        when(dishes.list(7L)).thenReturn(List.of(view()));
        when(dishes.get(7L, 100L)).thenReturn(view());

        mvc.perform(
                        get("/api/composite-dishes")
                                .cookie(
                                        new jakarta.servlet.http.Cookie(
                                                "foodmate_session", "session-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].composite_dish_id", is("100")));

        mvc.perform(
                        get("/api/composite-dishes/100")
                                .cookie(
                                        new jakarta.servlet.http.Cookie(
                                                "foodmate_session", "session-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dish_name", is("鸡肉饭")));

        verify(dishes).list(7L);
        verify(dishes).get(7L, 100L);
    }

    @Test
    void updatesCompositeDishWithRevisionAndIdempotencyKey() throws Exception {
        when(accounts.requireSessionUser("session-1")).thenReturn(user());
        when(dishes.update(eq(7L), eq(100L), eq(2L), any(CompositeDishService.UpdateCommand.class)))
                .thenReturn(view());

        mvc.perform(
                        patch("/api/composite-dishes/100")
                                .cookie(
                                        new jakarta.servlet.http.Cookie(
                                                "foodmate_session", "session-1"))
                                .param("revision", "2")
                                .header("Idempotency-Key", "update-1")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"dish_name\":\"更新后的鸡肉饭\",\"total_servings\":2,\"components\":[{\"nutrition_food_id\":171477,\"raw_name\":\"熟鸡胸肉\",\"amount\":300,\"unit\":\"g\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.revision", is(2)));

        verify(dishes)
                .update(eq(7L), eq(100L), eq(2L), any(CompositeDishService.UpdateCommand.class));
    }

    @Test
    void deletesCompositeDishWithRevisionAndIdempotencyKey() throws Exception {
        when(accounts.requireSessionUser("session-1")).thenReturn(user());

        mvc.perform(
                        delete("/api/composite-dishes/100")
                                .cookie(
                                        new jakarta.servlet.http.Cookie(
                                                "foodmate_session", "session-1"))
                                .param("revision", "2")
                                .header("Idempotency-Key", "delete-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)));

        verify(dishes).delete(7L, 100L, 2L, "delete-1");
    }

    private UserAccountService.UserRecord user() {
        return new UserAccountService.UserRecord(
                7L, "user", "user@example.com", "hash", "user", "User", "active");
    }

    private CompositeDishService.CompositeDishView view() {
        return new CompositeDishService.CompositeDishView(
                100L,
                "鸡肉饭",
                new BigDecimal("2"),
                new BigDecimal("330.0000"),
                new BigDecimal("31.0000"),
                new BigDecimal("7.0000"),
                new BigDecimal("42.0000"),
                "USDA:2025",
                2L,
                false,
                Instant.parse("2026-08-12T12:00:00Z"),
                Instant.parse("2026-08-12T12:00:00Z"),
                List.of(
                        new CompositeDishService.ComponentView(
                                201L,
                                0,
                                171477L,
                                "熟鸡胸肉",
                                new BigDecimal("300"),
                                "g",
                                new BigDecimal("300.000"),
                                "g",
                                new BigDecimal("495.0000"),
                                new BigDecimal("93.0000"),
                                new BigDecimal("10.8000"),
                                BigDecimal.ZERO.setScale(4))));
    }
}
