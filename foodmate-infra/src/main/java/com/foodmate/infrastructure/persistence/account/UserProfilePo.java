package com.foodmate.infrastructure.persistence.account;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.foodmate.infrastructure.persistence.BasePo;

@TableName("user_profiles")
public class UserProfilePo extends BasePo {
    @TableId("profile_id") public Long profileId;
    public Long userId;
    public String displayName;
    public String gender;
    public java.time.LocalDate birthday;
    public java.math.BigDecimal heightCm;
    public java.math.BigDecimal weightKg;
    public String activityLevel;
    public String dietGoal;
    public Integer calorieTarget;
    public Integer proteinTarget;
    public String allergens;
    public String dislikes;
    public String preferredUnits;
    public String profileJson;
}
