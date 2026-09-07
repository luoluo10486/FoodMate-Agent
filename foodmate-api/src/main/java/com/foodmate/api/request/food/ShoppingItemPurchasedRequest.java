package com.foodmate.api.request.food;

import jakarta.validation.constraints.NotNull;

/** 购物项购买状态更新参数。 */
public record ShoppingItemPurchasedRequest(@NotNull Boolean purchased) {}
