package com.foodmate.api.controller;

import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foodmate.api.advice.GlobalExceptionHandler;
import com.foodmate.api.controller.food.NutritionAnalysisController;
import com.foodmate.api.filter.TraceContextFilter;
import com.foodmate.application.account.service.UserAccountService;
import com.foodmate.application.food.service.NutritionAnalysisService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class NutritionAnalysisControllerTest {
    private UserAccountService accounts;
    private NutritionAnalysisService analysis;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        accounts = mock(UserAccountService.class);
        analysis = mock(NutritionAnalysisService.class);
        mvc =
                MockMvcBuilders.standaloneSetup(new NutritionAnalysisController(accounts, analysis))
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .addFilters(new TraceContextFilter())
                        .build();
    }

    @Test
    void readsAnalysisForAuthenticatedUser() throws Exception {
        when(accounts.requireSessionUser("session-1")).thenReturn(user());
        when(analysis.analyze(7L, "7d"))
                .thenReturn(
                        new NutritionAnalysisService.Analysis(
                                "7d",
                                Instant.parse("2026-08-05T00:00:00Z"),
                                Instant.parse("2026-08-12T00:00:00Z"),
                                1,
                                1,
                                new BigDecimal("1.0000"),
                                new BigDecimal("130.00"),
                                new BigDecimal("2.7000"),
                                new BigDecimal("0.3000"),
                                new BigDecimal("28.2000"),
                                1800,
                                120,
                                false,
                                List.of(),
                                "disclaimer"));

        mvc.perform(
                        get("/api/nutrition-analysis")
                                .param("range", "7d")
                                .cookie(
                                        new jakarta.servlet.http.Cookie(
                                                "foodmate_session", "session-1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.range", is("7d")))
                .andExpect(jsonPath("$.data.matched_items", is(1)))
                .andExpect(jsonPath("$.data.incomplete", is(false)));
    }

    private UserAccountService.UserRecord user() {
        return new UserAccountService.UserRecord(
                7L, "user", "user@example.com", "hash", "user", "User", "active");
    }
}
