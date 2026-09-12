package com.foodmate.infrastructure.persistence.food;

import com.foodmate.application.food.port.out.MealPlanRepository.MealSlotSnapshot;
import com.foodmate.application.food.port.out.MealPlanRepository.MealSlotWrite;
import com.foodmate.application.food.port.out.MealPlanRepository.PlanSnapshot;
import com.foodmate.application.food.port.out.MealPlanRepository.PlanWrite;
import com.foodmate.application.food.port.out.MealPlanRepository.ShoppingItemSnapshot;
import com.foodmate.application.food.port.out.MealPlanRepository.ShoppingItemWrite;
import com.foodmate.application.food.port.out.MealPlanRepository.ShoppingListSnapshot;
import com.foodmate.application.food.port.out.MealPlanRepository.ShoppingListWrite;
import com.foodmate.application.food.port.out.MealPlanRepository.UpdatePlanWrite;
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
            "SELECT EXISTS(SELECT 1 FROM sessions WHERE session_id=#{sessionId} AND"
                    + " user_id=#{userId} AND is_deleted=FALSE)")
    boolean sessionOwned(@Param("userId") long userId, @Param("sessionId") long sessionId);

    @Insert(
            "INSERT INTO"
                    + " meal_plans(meal_plan_id,user_id,session_id,plan_name,days,budget,constraints_json,plan_json,validation_json,status,idempotency_key,revision,created_by,updated_by)"
                    + " VALUES"
                    + " (#{mealPlanId},#{userId},#{sessionId},#{planName},#{days},#{budget},CAST(#{constraintsJson}"
                    + " AS jsonb),CAST(#{planJson} AS jsonb),CAST(#{validationJson} AS"
                    + " jsonb),#{status},#{idempotencyKey},#{revision},#{userId},#{userId})")
    int insertPlan(PlanWrite plan);

    @Update(
            "UPDATE meal_plans SET status=#{status},validation_json=CAST(#{validationJson} AS"
                    + " jsonb),updated_at=CURRENT_TIMESTAMP,updated_by=#{userId},revision=revision+1"
                    + " WHERE meal_plan_id=#{mealPlanId} AND user_id=#{userId} AND"
                    + " revision=#{expectedRevision} AND is_deleted=FALSE")
    int updatePlanStatus(
            @Param("userId") long userId,
            @Param("mealPlanId") long mealPlanId,
            @Param("expectedRevision") long expectedRevision,
            @Param("status") String status,
            @Param("validationJson") String validationJson);

    @Update(
            "UPDATE meal_plans SET status=#{status},validation_json=CAST(#{validationJson} AS"
                    + " jsonb),updated_at=CURRENT_TIMESTAMP,updated_by=#{userId},revision=revision+1"
                    + " WHERE meal_plan_id=#{mealPlanId} AND user_id=#{userId} AND is_deleted=FALSE")
    int updatePlanStatusLegacy(
            @Param("userId") long userId,
            @Param("mealPlanId") long mealPlanId,
            @Param("status") String status,
            @Param("validationJson") String validationJson);

    @Update(
            "UPDATE meal_plans SET"
                    + " plan_name=#{planName},days=#{days},budget=#{budget},constraints_json=CAST(#{constraintsJson}"
                    + " AS jsonb),plan_json=CAST(#{planJson} AS"
                    + " jsonb),validation_json=CAST(#{validationJson} AS"
                    + " jsonb),status='draft',updated_at=CURRENT_TIMESTAMP,updated_by=#{userId},revision=revision+1"
                    + " WHERE meal_plan_id=#{mealPlanId} AND user_id=#{userId} AND"
                    + " revision=#{expectedRevision} AND is_deleted=FALSE")
    int updatePlan(UpdatePlanWrite plan);

    @Select(
            "SELECT meal_plan_id AS mealPlanId,user_id AS userId,session_id AS sessionId,plan_name"
                    + " AS planName,days,budget,constraints_json::text AS"
                    + " constraintsJson,plan_json::text AS planJson,validation_json::text AS"
                    + " validationJson,status,idempotency_key AS idempotencyKey,revision,is_deleted AS"
                    + " deleted,created_at AS createdAt,updated_at AS updatedAt FROM meal_plans WHERE"
                    + " meal_plan_id=#{mealPlanId} AND user_id=#{userId} AND"
                    + " is_deleted=#{includeDeleted}")
    PlanSnapshot findOwnedPlan(
            @Param("userId") long userId,
            @Param("mealPlanId") long mealPlanId,
            @Param("includeDeleted") boolean includeDeleted);

    @Select(
            "SELECT meal_plan_id AS mealPlanId,user_id AS userId,session_id AS sessionId,plan_name"
                    + " AS planName,days,budget,constraints_json::text AS"
                    + " constraintsJson,plan_json::text AS planJson,validation_json::text AS"
                    + " validationJson,status,idempotency_key AS idempotencyKey,revision,is_deleted AS"
                    + " deleted,created_at AS createdAt,updated_at AS updatedAt FROM meal_plans WHERE"
                    + " user_id=#{userId} AND (#{includeDeleted}=TRUE OR is_deleted=FALSE) ORDER BY"
                    + " is_deleted ASC,updated_at DESC,meal_plan_id DESC")
    List<PlanSnapshot> findOwnedPlans(
            @Param("userId") long userId, @Param("includeDeleted") boolean includeDeleted);

    @Update(
            "UPDATE meal_plans SET"
                    + " is_deleted=TRUE,deleted_at=CURRENT_TIMESTAMP,deleted_by=#{userId},updated_at=CURRENT_TIMESTAMP,updated_by=#{userId},revision=revision+1"
                    + " WHERE meal_plan_id=#{mealPlanId} AND user_id=#{userId} AND revision=#{revision}"
                    + " AND is_deleted=FALSE")
    int softDelete(
            @Param("userId") long userId,
            @Param("mealPlanId") long mealPlanId,
            @Param("revision") long revision);

    @Update(
            "UPDATE meal_plans SET"
                    + " is_deleted=FALSE,deleted_at=NULL,deleted_by=NULL,updated_at=CURRENT_TIMESTAMP,updated_by=#{userId},revision=revision+1"
                    + " WHERE meal_plan_id=#{mealPlanId} AND user_id=#{userId} AND revision=#{revision}"
                    + " AND is_deleted=TRUE")
    int restore(
            @Param("userId") long userId,
            @Param("mealPlanId") long mealPlanId,
            @Param("revision") long revision);

    @Update(
            "UPDATE shopping_lists SET"
                    + " is_deleted=TRUE,deleted_at=CURRENT_TIMESTAMP,deleted_by=#{userId},updated_at=CURRENT_TIMESTAMP,updated_by=#{userId}"
                    + " WHERE meal_plan_id=#{mealPlanId} AND user_id=#{userId} AND is_deleted=FALSE")
    int softDeleteShoppingList(@Param("userId") long userId, @Param("mealPlanId") long mealPlanId);

    @Insert(
            "INSERT INTO"
                    + " shopping_lists(shopping_list_id,meal_plan_id,user_id,items_json,status,created_by,updated_by)"
                    + " VALUES (#{shoppingListId},#{mealPlanId},#{userId},CAST(#{itemsJson} AS"
                    + " jsonb),#{status},#{userId},#{userId})")
    int insertShoppingList(ShoppingListWrite list);

    @Select(
            "SELECT shopping_list_id AS shoppingListId,meal_plan_id AS mealPlanId,user_id AS"
                    + " userId,items_json::text AS itemsJson,status,created_at AS createdAt,updated_at"
                    + " AS updatedAt FROM shopping_lists WHERE meal_plan_id=#{mealPlanId} AND"
                    + " user_id=#{userId} AND is_deleted=FALSE ORDER BY created_at DESC LIMIT 1")
    ShoppingListSnapshot findOwnedShoppingList(
            @Param("userId") long userId, @Param("mealPlanId") long mealPlanId);

    @Select(
            "SELECT shopping_list_id AS shoppingListId,meal_plan_id AS mealPlanId,user_id AS"
                    + " userId,items_json::text AS itemsJson,status,created_at AS createdAt,updated_at"
                    + " AS updatedAt FROM shopping_lists WHERE meal_plan_id=#{mealPlanId} AND"
                    + " user_id=#{userId} AND is_deleted=FALSE ORDER BY created_at DESC LIMIT 1")
    ShoppingListSnapshot findLatestShoppingList(
            @Param("userId") long userId, @Param("mealPlanId") long mealPlanId);

    @Update(
            "UPDATE shopping_lists SET items_json=CAST(#{itemsJson} AS"
                    + " jsonb),updated_at=CURRENT_TIMESTAMP,updated_by=#{userId} WHERE"
                    + " shopping_list_id=#{shoppingListId} AND user_id=#{userId} AND is_deleted=FALSE")
    int updateShoppingListItems(
            @Param("userId") long userId,
            @Param("shoppingListId") long shoppingListId,
            @Param("itemsJson") String itemsJson);

    @Insert(
            "INSERT INTO"
                    + " meal_plan_meals(meal_plan_meal_id,meal_plan_id,user_id,day_index,meal_type,meal_name,meal_json,plan_revision,created_by,updated_by)"
                    + " VALUES"
                    + " (#{mealPlanMealId},#{mealPlanId},#{userId},#{dayIndex},#{mealType},#{mealName},CAST(#{mealJson}"
                    + " AS jsonb),#{planRevision},#{userId},#{userId}) ON CONFLICT"
                    + " (meal_plan_id,day_index,meal_type) WHERE is_deleted=FALSE DO UPDATE SET"
                    + " meal_name=EXCLUDED.meal_name,meal_json=EXCLUDED.meal_json,plan_revision=EXCLUDED.plan_revision,updated_at=CURRENT_TIMESTAMP,updated_by=EXCLUDED.updated_by,is_deleted=FALSE,deleted_at=NULL,deleted_by=NULL")
    int upsertMealSlot(MealSlotWrite slot);

    @Select(
            "SELECT m.meal_plan_meal_id AS mealPlanMealId,m.meal_plan_id AS mealPlanId,m.user_id AS"
                    + " userId,m.day_index AS dayIndex,m.meal_type AS mealType,m.meal_name AS"
                    + " mealName,m.meal_json::text AS mealJson,m.plan_revision AS planRevision,(SELECT"
                    + " COUNT(*) FROM food_logs f WHERE f.meal_plan_meal_id=m.meal_plan_meal_id AND"
                    + " f.user_id=m.user_id AND f.is_deleted=FALSE) AS foodLogCount,m.updated_at AS"
                    + " updatedAt FROM meal_plan_meals m WHERE m.user_id=#{userId} AND"
                    + " m.meal_plan_id=#{mealPlanId} AND m.is_deleted=FALSE ORDER BY"
                    + " m.day_index,m.meal_type")
    List<MealSlotSnapshot> findMealSlots(
            @Param("userId") long userId, @Param("mealPlanId") long mealPlanId);

    @Update(
            "UPDATE meal_plan_meals SET"
                    + " is_deleted=TRUE,deleted_at=CURRENT_TIMESTAMP,deleted_by=#{userId},updated_at=CURRENT_TIMESTAMP,updated_by=#{userId}"
                    + " WHERE user_id=#{userId} AND meal_plan_id=#{mealPlanId} AND is_deleted=FALSE")
    int deactivateMealSlots(@Param("userId") long userId, @Param("mealPlanId") long mealPlanId);

    @Update(
            "<script>UPDATE meal_plan_meals SET"
                    + " is_deleted=TRUE,deleted_at=CURRENT_TIMESTAMP,deleted_by=#{userId},updated_at=CURRENT_TIMESTAMP,updated_by=#{userId}"
                    + " WHERE user_id=#{userId} AND meal_plan_id=#{mealPlanId} AND is_deleted=FALSE <if"
                    + " test='activeSlotKeys != null and activeSlotKeys.size() &gt; 0'>AND"
                    + " (day_index::text || ':' || meal_type) NOT IN <foreach"
                    + " collection='activeSlotKeys' item='slotKey' open='(' separator=','"
                    + " close=')'>#{slotKey}</foreach></if></script>")
    int softDeleteMealSlotsNotInKeys(
            @Param("userId") long userId,
            @Param("mealPlanId") long mealPlanId,
            @Param("activeSlotKeys") List<String> activeSlotKeys);

    @Insert(
            "INSERT INTO"
                    + " shopping_list_items(shopping_list_item_id,shopping_list_id,meal_plan_id,user_id,item_key,item_name,amount,unit,created_by,updated_by)"
                    + " VALUES"
                    + " (#{shoppingListItemId},#{shoppingListId},#{mealPlanId},#{userId},#{itemKey},#{itemName},#{amount},#{unit},#{userId},#{userId})"
                    + " ON CONFLICT (shopping_list_id,item_key) WHERE is_deleted=FALSE DO UPDATE SET"
                    + " item_name=EXCLUDED.item_name,amount=CASE WHEN shopping_list_items.amount IS NOT"
                    + " DISTINCT FROM EXCLUDED.amount AND shopping_list_items.unit IS NOT DISTINCT FROM"
                    + " EXCLUDED.unit THEN shopping_list_items.amount ELSE EXCLUDED.amount"
                    + " END,unit=EXCLUDED.unit,purchased=CASE WHEN shopping_list_items.amount IS NOT"
                    + " DISTINCT FROM EXCLUDED.amount AND shopping_list_items.unit IS NOT DISTINCT FROM"
                    + " EXCLUDED.unit THEN shopping_list_items.purchased ELSE FALSE"
                    + " END,purchased_at=CASE WHEN shopping_list_items.amount IS NOT DISTINCT FROM"
                    + " EXCLUDED.amount AND shopping_list_items.unit IS NOT DISTINCT FROM EXCLUDED.unit"
                    + " THEN shopping_list_items.purchased_at ELSE NULL"
                    + " END,updated_at=CURRENT_TIMESTAMP,updated_by=EXCLUDED.updated_by,is_deleted=FALSE,deleted_at=NULL,deleted_by=NULL")
    int upsertShoppingItem(ShoppingItemWrite item);

    @Select(
            "SELECT shopping_list_item_id AS shoppingListItemId,shopping_list_id AS"
                    + " shoppingListId,meal_plan_id AS mealPlanId,user_id AS userId,item_key AS"
                    + " itemKey,item_name AS itemName,amount,unit,purchased,purchased_at AS"
                    + " purchasedAt,updated_at AS updatedAt FROM shopping_list_items WHERE"
                    + " user_id=#{userId} AND meal_plan_id=#{mealPlanId} AND"
                    + " shopping_list_id=#{shoppingListId} AND is_deleted=FALSE ORDER BY"
                    + " shopping_list_item_id")
    List<ShoppingItemSnapshot> findShoppingItems(
            @Param("userId") long userId,
            @Param("mealPlanId") long mealPlanId,
            @Param("shoppingListId") long shoppingListId);

    @Update(
            "<script>UPDATE shopping_list_items SET"
                    + " is_deleted=TRUE,deleted_at=CURRENT_TIMESTAMP,deleted_by=#{userId},updated_at=CURRENT_TIMESTAMP,updated_by=#{userId}"
                    + " WHERE user_id=#{userId} AND shopping_list_id=#{shoppingListId} AND"
                    + " is_deleted=FALSE <if test='activeItemKeys != null and activeItemKeys.size()"
                    + " &gt; 0'>AND item_key NOT IN <foreach collection='activeItemKeys' item='itemKey'"
                    + " open='(' separator=',' close=')'>#{itemKey}</foreach></if></script>")
    int softDeleteShoppingItemsNotInKeys(
            @Param("userId") long userId,
            @Param("shoppingListId") long shoppingListId,
            @Param("activeItemKeys") List<String> activeItemKeys);

    @Update(
            "UPDATE shopping_list_items SET purchased=#{purchased},purchased_at=CASE WHEN"
                    + " #{purchased}=TRUE THEN CURRENT_TIMESTAMP ELSE NULL"
                    + " END,updated_at=CURRENT_TIMESTAMP,updated_by=#{userId} WHERE"
                    + " shopping_list_item_id=#{shoppingListItemId} AND user_id=#{userId} AND"
                    + " is_deleted=FALSE")
    int updateShoppingItemPurchased(
            @Param("userId") long userId,
            @Param("shoppingListItemId") long shoppingListItemId,
            @Param("purchased") boolean purchased);
}
