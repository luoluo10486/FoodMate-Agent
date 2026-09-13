package com.foodmate.api.request.account;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

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
        Integer proteinTarget,
        List<@Size(max = 128) String> allergens,
        List<@Size(max = 128) String> dislikes,
        PreferredUnits preferredUnits) {
    /** 用户偏好的展示单位。 */
    public record PreferredUnits(String weight, String energy) {}
}
