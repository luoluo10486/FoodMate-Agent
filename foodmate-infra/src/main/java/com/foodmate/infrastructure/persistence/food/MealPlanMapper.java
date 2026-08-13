package com.foodmate.infrastructure.persistence.food;

import com.foodmate.application.food.port.out.MealPlanRepository.*;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 餐食计划和购物清单的 MyBatis 映射。 */
@Mapper
public interface MealPlanMapper {
    @Select(
            "SELECT EXISTS(SELECT 1 FROM sessions WHERE session_id=#{sessionId} AND user_id=#{userId} AND is_deleted=FALSE)")
    boolean sessionOwned(@Param("userId") long userId, @Param("sessionId") long sessionId);

    @Insert(
            "INSERT INTO meal_plans(meal_plan_id,user_id,session_id,plan_name,days,budget,constraints_json,plan_json,validation_json,status,created_by,updated_by) VALUES (#{mealPlanId},#{userId},#{sessionId},#{planName},#{days},#{budget},CAST(#{constraintsJson} AS jsonb),CAST(#{planJson} AS jsonb),CAST(#{validationJson} AS jsonb),#{status},#{userId},#{userId})")
    int insertPlan(PlanWrite plan);

    @Update(
            "UPDATE meal_plans SET status=#{status},validation_json=CAST(#{validationJson} AS jsonb),updated_at=CURRENT_TIMESTAMP,updated_by=#{userId} WHERE meal_plan_id=#{mealPlanId} AND user_id=#{userId} AND is_deleted=FALSE")
    int updatePlanStatus(
            @Param("userId") long userId,
            @Param("mealPlanId") long mealPlanId,
            @Param("status") String status,
            @Param("validationJson") String validationJson);

    @Select(
            "SELECT meal_plan_id AS mealPlanId,user_id AS userId,session_id AS sessionId,plan_name AS planName,days,budget,constraints_json::text AS constraintsJson,plan_json::text AS planJson,validation_json::text AS validationJson,status,created_at AS createdAt,updated_at AS updatedAt FROM meal_plans WHERE meal_plan_id=#{mealPlanId} AND user_id=#{userId} AND is_deleted=FALSE")
    PlanSnapshot findOwnedPlan(@Param("userId") long userId, @Param("mealPlanId") long mealPlanId);

    @Insert(
            "INSERT INTO shopping_lists(shopping_list_id,meal_plan_id,user_id,items_json,status,created_by,updated_by) VALUES (#{shoppingListId},#{mealPlanId},#{userId},CAST(#{itemsJson} AS jsonb),#{status},#{userId},#{userId})")
    int insertShoppingList(ShoppingListWrite list);

    @Select(
            "SELECT shopping_list_id AS shoppingListId,meal_plan_id AS mealPlanId,user_id AS userId,items_json::text AS itemsJson,status,created_at AS createdAt,updated_at AS updatedAt FROM shopping_lists WHERE meal_plan_id=#{mealPlanId} AND user_id=#{userId} AND is_deleted=FALSE ORDER BY created_at DESC LIMIT 1")
    ShoppingListSnapshot findOwnedShoppingList(
            @Param("userId") long userId, @Param("mealPlanId") long mealPlanId);
}
