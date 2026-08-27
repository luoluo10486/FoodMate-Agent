package com.foodmate.application.runtime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodmate.application.runtime.service.PlanValidator;
import org.junit.jupiter.api.Test;

class PlanValidatorTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void acceptsACompletePlanAndCalculatesKnownSummaries() throws Exception {
        var plan =
                mapper.readTree(
                        """
                        {"people":2,"days":1,"budget":20,"calorie_target":500,"protein_target":20,
                         "days_plan":[{"breakfast":{"cost":8,"calories_kcal":600,"protein_g":25},
                         "lunch":{"cost":6,"calories_kcal":300,"protein_g":10},
                         "dinner":{"cost":5,"calories_kcal":200,"protein_g":8}}]}
                        """);

        var result = PlanValidator.evaluate(plan);

        assertTrue(result.valid());
        assertTrue(result.budgetSummary().path("known").asBoolean());
        assertTrue(result.nutritionSummary().path("protein_g").asDouble() > 40);
    }

    @Test
    void reportsMissingMealsAndBudgetAndAllergenViolations() throws Exception {
        var plan =
                mapper.readTree(
                        """
                        {"people":1,"days":1,"budget":5,"allergens":["花生"],
                         "days_plan":[{"breakfast":{"cost":6,"ingredients":[{"name":"花生酱"}]}}]}
                        """);

        var result = PlanValidator.evaluate(plan);

        assertFalse(result.valid());
        assertTrue(result.issues().toString().contains("lunch"));
        assertTrue(result.issues().toString().contains("预算"));
        assertTrue(result.issues().toString().contains("过敏原"));
    }
}
