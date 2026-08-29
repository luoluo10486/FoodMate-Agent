package com.foodmate.api.response.food;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.Instant;

/** 餐食计划购物清单响应。 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ShoppingListResponse(
        String shoppingListId,
        String mealPlanId,
        JsonNode items,
        String status,
        Instant createdAt,
        Instant updatedAt) {}
