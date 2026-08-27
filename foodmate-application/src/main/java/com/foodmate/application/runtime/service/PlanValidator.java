package com.foodmate.application.runtime.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Applies deterministic, side-effect-free business rules to a proposed meal plan. */
public final class PlanValidator {
    private static final Set<String> REQUIRED_MEALS = Set.of("breakfast", "lunch", "dinner");
    private static final int MAX_DAYS = 31;
    private static final int MAX_PEOPLE = 20;
    private static final int MAX_INPUT_LENGTH = 200_000;
    private static final BigDecimal MAX_NUMBER = new BigDecimal("1000000000000");

    private PlanValidator() {}

    public static Validation evaluate(JsonNode plan) {
        ArrayNode issues = JsonNodeFactory.instance.arrayNode();
        ArrayNode warnings = JsonNodeFactory.instance.arrayNode();
        ObjectNode nutrition = JsonNodeFactory.instance.objectNode();
        ObjectNode budget = JsonNodeFactory.instance.objectNode();
        budget.put("known", false);
        if (plan == null || !plan.isObject() || plan.toString().length() > MAX_INPUT_LENGTH) {
            issues.add("plan 必须是大小受限的对象");
            return new Validation(false, issues, warnings, nutrition, budget);
        }

        JsonNode constraints =
                object(plan.get("constraints")) != null ? plan.get("constraints") : plan;
        int people = positiveInt(first(plan, constraints, "people"));
        int days = positiveInt(first(plan, constraints, "days"));
        if (people < 1 || people > MAX_PEOPLE) issues.add("people 必须在 1 到 20 之间");
        if (days < 1 || days > MAX_DAYS) issues.add("days 必须在 1 到 31 之间");

        JsonNode daysPlan = plan.get("days_plan");
        if (daysPlan == null) daysPlan = plan.get("daysPlan");
        if (daysPlan == null || !daysPlan.isArray() || daysPlan.size() != days) {
            issues.add("days_plan 必须包含与 days 相同数量的天");
        } else {
            inspectDays(daysPlan, constraints, issues, warnings, nutrition, budget);
        }

        BigDecimal targetBudget = decimal(first(plan, constraints, "budget"));
        if (targetBudget != null) {
            if (targetBudget.signum() < 0 || targetBudget.compareTo(MAX_NUMBER) > 0)
                issues.add("budget 必须是非负且有界的数字");
            else {
                budget.put("limit", targetBudget.setScale(2, RoundingMode.HALF_UP));
                BigDecimal total = decimal(budget.get("total"));
                if (total != null && total.compareTo(targetBudget) > 0) issues.add("计划预算超过上限");
            }
        }
        compareTarget(constraints, nutrition, issues, "calorie_target", "calories_kcal");
        compareTarget(constraints, nutrition, issues, "protein_target", "protein_g");
        return new Validation(issues.isEmpty(), issues, warnings, nutrition, budget);
    }

    private static void inspectDays(
            JsonNode daysPlan,
            JsonNode constraints,
            ArrayNode issues,
            ArrayNode warnings,
            ObjectNode nutrition,
            ObjectNode budget) {
        List<String> allergens = strings(constraints.get("allergens"));
        List<String> dislikes = strings(constraints.get("dislikes"));
        BigDecimal totalCost = BigDecimal.ZERO;
        boolean costKnown = false;
        BigDecimal calories = BigDecimal.ZERO;
        BigDecimal protein = BigDecimal.ZERO;
        BigDecimal fat = BigDecimal.ZERO;
        BigDecimal carbs = BigDecimal.ZERO;
        for (int index = 0; index < daysPlan.size(); index++) {
            JsonNode day = daysPlan.get(index);
            if (!day.isObject()) {
                issues.add("第 " + (index + 1) + " 天必须是对象");
                continue;
            }
            String serialized = day.toString().toLowerCase(Locale.ROOT);
            for (String allergen : allergens)
                if (serialized.contains(allergen.toLowerCase(Locale.ROOT)))
                    issues.add("第 " + (index + 1) + " 天包含过敏原: " + allergen);
            for (String dislike : dislikes)
                if (serialized.contains(dislike.toLowerCase(Locale.ROOT)))
                    warnings.add("第 " + (index + 1) + " 天包含忌口: " + dislike);
            for (String meal : REQUIRED_MEALS) {
                JsonNode mealNode = day.get(meal);
                if (mealNode == null || !mealNode.isObject()) {
                    issues.add("第 " + (index + 1) + " 天缺少 " + meal);
                    continue;
                }
                BigDecimal mealCost = number(mealNode, "cost");
                if (mealCost == null) mealCost = number(mealNode, "estimated_cost");
                if (mealCost != null) {
                    if (mealCost.signum() < 0) issues.add("餐食成本必须是非负数字");
                    totalCost = totalCost.add(mealCost);
                    costKnown = true;
                } else {
                    JsonNode ingredients = mealNode.get("ingredients");
                    if (ingredients != null && ingredients.isArray())
                        for (JsonNode ingredient : ingredients) {
                            if (!ingredient.isObject()) continue;
                            BigDecimal itemCost = number(ingredient, "cost");
                            if (itemCost == null) itemCost = number(ingredient, "estimated_cost");
                            if (itemCost == null) itemCost = number(ingredient, "price");
                            if (itemCost != null) {
                                if (itemCost.signum() < 0) issues.add("食材成本必须是非负数字");
                                totalCost = totalCost.add(itemCost);
                                costKnown = true;
                            }
                        }
                }
                calories = addMetric(calories, mealNode, "calories_kcal", "calories");
                protein = addMetric(protein, mealNode, "protein_g", "protein");
                fat = addMetric(fat, mealNode, "fat_g", "fat");
                carbs = addMetric(carbs, mealNode, "carbs_g", "carbs");
            }
        }
        if (costKnown) {
            budget.put("known", true);
            budget.put("total", totalCost.setScale(2, RoundingMode.HALF_UP));
        }
        nutrition.put("calories_kcal", calories.setScale(3, RoundingMode.HALF_UP));
        nutrition.put("protein_g", protein.setScale(3, RoundingMode.HALF_UP));
        nutrition.put("fat_g", fat.setScale(3, RoundingMode.HALF_UP));
        nutrition.put("carbs_g", carbs.setScale(3, RoundingMode.HALF_UP));
    }

    private static void compareTarget(
            JsonNode constraints,
            ObjectNode nutrition,
            ArrayNode issues,
            String targetName,
            String actualName) {
        BigDecimal target = number(constraints, targetName);
        if (target == null) return;
        if (target.signum() < 0 || target.compareTo(MAX_NUMBER) > 0) {
            issues.add(targetName + " 必须是非负且有界的数字");
            return;
        }
        nutrition.put(targetName, target.setScale(3, RoundingMode.HALF_UP));
        if (nutrition.path(actualName).decimalValue().compareTo(target) < 0)
            issues.add(actualName + " 未达到目标");
    }

    private static BigDecimal addMetric(
            BigDecimal current, JsonNode object, String primaryName, String fallbackName) {
        BigDecimal value = number(object, primaryName);
        if (value == null) value = number(object, fallbackName);
        return value == null ? current : current.add(value);
    }

    private static JsonNode first(JsonNode primary, JsonNode fallback, String name) {
        JsonNode value = primary.get(name);
        return value == null ? fallback.get(name) : value;
    }

    private static JsonNode object(JsonNode value) {
        return value != null && value.isObject() ? value : null;
    }

    private static int positiveInt(JsonNode value) {
        return value != null && value.isIntegralNumber() ? value.asInt(-1) : -1;
    }

    private static BigDecimal decimal(JsonNode value) {
        return value == null || !value.isNumber() ? null : safeDecimal(value);
    }

    private static BigDecimal number(JsonNode object, String name) {
        return object == null || !object.isObject() ? null : decimal(object.get(name));
    }

    private static BigDecimal safeDecimal(JsonNode value) {
        BigDecimal decimal = value.decimalValue();
        return decimal.abs().compareTo(MAX_NUMBER) <= 0 ? decimal : null;
    }

    private static List<String> strings(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (node == null || !node.isArray()) return values;
        for (JsonNode value : node)
            if (value.isTextual() && !value.asText().isBlank() && value.asText().length() <= 128)
                values.add(value.asText());
        return values;
    }

    public record Validation(
            boolean valid,
            ArrayNode issues,
            ArrayNode warnings,
            ObjectNode nutritionSummary,
            ObjectNode budgetSummary) {
        public ObjectNode asJson() {
            ObjectNode result = JsonNodeFactory.instance.objectNode();
            result.put("valid", valid);
            result.put("status", valid ? "valid" : "invalid");
            result.set("issues", issues.deepCopy());
            result.set("warnings", warnings.deepCopy());
            result.set("nutrition_summary", nutritionSummary.deepCopy());
            result.set("budget_summary", budgetSummary.deepCopy());
            return result;
        }
    }
}
