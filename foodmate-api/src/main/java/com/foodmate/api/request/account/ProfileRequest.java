package com.foodmate.api.request.account;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import java.math.BigDecimal;

/** 当前用户资料修改请求参数。 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record ProfileRequest(
        String displayName,
        String gender,
        @DecimalMin("30") @DecimalMax("250") BigDecimal heightCm,
        @DecimalMin("2") @DecimalMax("500") BigDecimal weightKg,
        String activityLevel,
        String dietGoal,
        Integer calorieTarget,
        Integer proteinTarget) {}
