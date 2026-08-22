package com.foodmate.infrastructure.persistence.food;

import com.foodmate.application.food.port.out.MealPlanRepository.*;
import java.util.List;
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
            "INSERT INTO meal_plans(meal_plan_id,user_id,session_id,plan_name,days,budget,constraints_json,plan_json,validation_json,status,idempotency_key,revision,created_by,updated_by) VALUES (#{mealPlanId},#{userId},#{sessionId},#{planName},#{days},#{budget},CAST(#{constraintsJson} AS jsonb),CAST(#{planJson} AS jsonb),CAST(#{validationJson} AS jsonb),#{status},#{idempotencyKey},#{revision},#{userId},#{userId})")
    int insertPlan(PlanWrite plan);

    @Update(
            "UPDATE meal_plans SET status=#{status},validation_json=CAST(#{validationJson} AS jsonb),updated_at=CURRENT_TIMESTAMP,updated_by=#{userId},revision=revision+1 WHERE meal_plan_id=#{mealPlanId} AND user_id=#{userId} AND revision=#{expectedRevision} AND is_deleted=FALSE")
    int updatePlanStatus(
            @Param("userId") long userId,
            @Param("mealPlanId") long mealPlanId,
            @Param("expectedRevision") long expectedRevision,
            @Param("status") String status,
            @Param("validationJson") String validationJson);

    @Update(
            "UPDATE meal_plans SET status=#{status},validation_json=CAST(#{validationJson} AS jsonb),updated_at=CURRENT_TIMESTAMP,updated_by=#{userId},revision=revision+1 WHERE meal_plan_id=#{mealPlanId} AND user_id=#{userId} AND is_deleted=FALSE")
    int updatePlanStatusLegacy(
            @Param("userId") long userId,
            @Param("mealPlanId") long mealPlanId,
            @Param("status") String status,
            @Param("validationJson") String validationJson);

    @Update(
            "UPDATE meal_plans SET plan_name=#{planName},days=#{days},budget=#{budget},constraints_json=CAST(#{constraintsJson} AS jsonb),plan_json=CAST(#{planJson} AS jsonb),validation_json=CAST(#{validationJson} AS jsonb),status='draft',updated_at=CURRENT_TIMESTAMP,updated_by=#{userId},revision=revision+1 WHERE meal_plan_id=#{mealPlanId} AND user_id=#{userId} AND revision=#{expectedRevision} AND is_deleted=FALSE")
    int updatePlan(UpdatePlanWrite plan);

    @Select(
            "SELECT meal_plan_id AS mealPlanId,user_id AS userId,session_id AS sessionId,plan_name AS planName,days,budget,constraints_json::text AS constraintsJson,plan_json::text AS planJson,validation_json::text AS validationJson,status,idempotency_key AS idempotencyKey,revision,is_deleted AS deleted,created_at AS createdAt,updated_at AS updatedAt FROM meal_plans WHERE meal_plan_id=#{mealPlanId} AND user_id=#{userId} AND is_deleted=#{includeDeleted}")
    PlanSnapshot findOwnedPlan(
            @Param("userId") long userId,
            @Param("mealPlanId") long mealPlanId,
            @Param("includeDeleted") boolean includeDeleted);

    @Select(
            "SELECT meal_plan_id AS mealPlanId,user_id AS userId,session_id AS sessionId,plan_name AS planName,days,budget,constraints_json::text AS constraintsJson,plan_json::text AS planJson,validation_json::text AS validationJson,status,idempotency_key AS idempotencyKey,revision,is_deleted AS deleted,created_at AS createdAt,updated_at AS updatedAt FROM meal_plans WHERE user_id=#{userId} AND (#{includeDeleted}=TRUE OR is_deleted=FALSE) ORDER BY is_deleted ASC,updated_at DESC,meal_plan_id DESC")
    List<PlanSnapshot> findOwnedPlans(
            @Param("userId") long userId, @Param("includeDeleted") boolean includeDeleted);

    @Update(
            "UPDATE meal_plans SET is_deleted=TRUE,deleted_at=CURRENT_TIMESTAMP,deleted_by=#{userId},updated_at=CURRENT_TIMESTAMP,updated_by=#{userId},revision=revision+1 WHERE meal_plan_id=#{mealPlanId} AND user_id=#{userId} AND revision=#{revision} AND is_deleted=FALSE")
    int softDelete(
            @Param("userId") long userId,
            @Param("mealPlanId") long mealPlanId,
            @Param("revision") long revision);

    @Update(
            "UPDATE meal_plans SET is_deleted=FALSE,deleted_at=NULL,deleted_by=NULL,updated_at=CURRENT_TIMESTAMP,updated_by=#{userId},revision=revision+1 WHERE meal_plan_id=#{mealPlanId} AND user_id=#{userId} AND revision=#{revision} AND is_deleted=TRUE")
    int restore(
            @Param("userId") long userId,
            @Param("mealPlanId") long mealPlanId,
            @Param("revision") long revision);

    @Update(
            "UPDATE shopping_lists SET is_deleted=TRUE,deleted_at=CURRENT_TIMESTAMP,deleted_by=#{userId},updated_at=CURRENT_TIMESTAMP,updated_by=#{userId} WHERE meal_plan_id=#{mealPlanId} AND user_id=#{userId} AND is_deleted=FALSE")
    int softDeleteShoppingList(@Param("userId") long userId, @Param("mealPlanId") long mealPlanId);

    @Insert(
            "INSERT INTO shopping_lists(shopping_list_id,meal_plan_id,user_id,items_json,status,created_by,updated_by) VALUES (#{shoppingListId},#{mealPlanId},#{userId},CAST(#{itemsJson} AS jsonb),#{status},#{userId},#{userId})")
    int insertShoppingList(ShoppingListWrite list);

    @Select(
            "SELECT shopping_list_id AS shoppingListId,meal_plan_id AS mealPlanId,user_id AS userId,items_json::text AS itemsJson,status,created_at AS createdAt,updated_at AS updatedAt FROM shopping_lists WHERE meal_plan_id=#{mealPlanId} AND user_id=#{userId} AND is_deleted=FALSE ORDER BY created_at DESC LIMIT 1")
    ShoppingListSnapshot findOwnedShoppingList(
            @Param("userId") long userId, @Param("mealPlanId") long mealPlanId);
}
