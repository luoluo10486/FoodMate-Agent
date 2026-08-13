package com.foodmate.infrastructure.persistence.food;

import com.foodmate.application.food.port.out.NutritionAnalysisRepository.*;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 饮食明细快照和用户营养目标的聚合查询。 */
@Mapper
public interface NutritionAnalysisMapper {
    @Select(
            "SELECT COUNT(i.food_log_item_id)::int AS totalItems,COUNT(*) FILTER (WHERE i.nutrition_status='matched')::int AS matchedItems,COALESCE(SUM(CASE WHEN i.nutrition_status='matched' THEN i.calories_kcal ELSE 0 END),0) AS caloriesKcal,COALESCE(SUM(CASE WHEN i.nutrition_status='matched' THEN i.protein_g ELSE 0 END),0) AS proteinG,COALESCE(SUM(CASE WHEN i.nutrition_status='matched' THEN i.fat_g ELSE 0 END),0) AS fatG,COALESCE(SUM(CASE WHEN i.nutrition_status='matched' THEN i.carbs_g ELSE 0 END),0) AS carbsG FROM food_logs f JOIN food_log_items i ON i.food_log_id=f.food_log_id AND i.is_deleted=FALSE WHERE f.user_id=#{userId} AND f.is_deleted=FALSE AND f.meal_time>=#{from} AND f.meal_time<#{to}")
    NutrientAggregate aggregate(
            @Param("userId") long userId, @Param("from") Instant from, @Param("to") Instant to);

    @Select(
            "SELECT DISTINCT i.raw_name FROM food_logs f JOIN food_log_items i ON i.food_log_id=f.food_log_id AND i.is_deleted=FALSE WHERE f.user_id=#{userId} AND f.is_deleted=FALSE AND f.meal_time>=#{from} AND f.meal_time<#{to} AND i.nutrition_status<>'matched' ORDER BY i.raw_name")
    List<String> unmatchedNames(
            @Param("userId") long userId, @Param("from") Instant from, @Param("to") Instant to);

    @Select(
            "SELECT calorie_target AS calorieTarget,protein_target AS proteinTarget FROM user_profiles WHERE user_id=#{userId} AND is_deleted=FALSE")
    Targets findTargets(@Param("userId") long userId);
}
