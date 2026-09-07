package com.foodmate.infrastructure.persistence.food;

import com.foodmate.application.food.port.out.CompositeDishRepository.DishWrite;
import com.foodmate.application.food.port.out.CompositeDishRepository.ItemSnapshot;
import com.foodmate.application.food.port.out.CompositeDishRepository.ItemWrite;
import com.foodmate.application.food.port.out.CompositeDishRepository.UpdateDishWrite;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 复合菜 MyBatis 映射；SQL 只处理持久化，不负责营养计算。 */
@Mapper
public interface CompositeDishMapper {
    record DishRow(
            long compositeDishId,
            long userId,
            String dishName,
            BigDecimal totalServings,
            BigDecimal caloriesKcalPerServing,
            BigDecimal proteinGPerServing,
            BigDecimal fatGPerServing,
            BigDecimal carbsGPerServing,
            String nutritionSource,
            long revision,
            boolean deleted,
            Instant createdAt,
            Instant updatedAt) {}

    @Insert(
            "INSERT INTO composite_dishes(composite_dish_id,user_id,dish_name,total_servings,calories_kcal_per_serving,protein_g_per_serving,fat_g_per_serving,carbs_g_per_serving,nutrition_source,idempotency_key,revision,created_by,updated_by) VALUES (#{compositeDishId},#{userId},#{dishName},#{totalServings},#{caloriesKcalPerServing},#{proteinGPerServing},#{fatGPerServing},#{carbsGPerServing},#{nutritionSource},#{idempotencyKey},#{revision},#{userId},#{userId})")
    int insertDish(DishWrite write);

    @Update(
            "UPDATE composite_dishes SET dish_name=#{dishName},total_servings=#{totalServings},calories_kcal_per_serving=#{caloriesKcalPerServing},protein_g_per_serving=#{proteinGPerServing},fat_g_per_serving=#{fatGPerServing},carbs_g_per_serving=#{carbsGPerServing},nutrition_source=#{nutritionSource},updated_at=CURRENT_TIMESTAMP,updated_by=#{userId},revision=revision+1 WHERE composite_dish_id=#{compositeDishId} AND user_id=#{userId} AND revision=#{expectedRevision} AND is_deleted=FALSE")
    int updateDish(UpdateDishWrite write);

    @Insert(
            "INSERT INTO composite_dish_items(composite_dish_item_id,composite_dish_id,item_order,nutrition_food_id,raw_name,amount,unit,normalized_amount,normalized_unit,conversion_id,calories_kcal,protein_g,fat_g,carbs_g,created_by,updated_by) VALUES (#{itemId},#{compositeDishId},#{itemOrder},#{nutritionFoodId},#{rawName},#{amount},#{unit},#{normalizedAmount},#{normalizedUnit},#{conversionId},#{caloriesKcal},#{proteinG},#{fatG},#{carbsG},#{userId},#{userId})")
    void insertItem(ItemWrite write);

    @Update(
            "UPDATE composite_dish_items SET is_deleted=TRUE,deleted_at=CURRENT_TIMESTAMP,deleted_by=#{userId},updated_at=CURRENT_TIMESTAMP,updated_by=#{userId} WHERE composite_dish_id=#{compositeDishId} AND is_deleted=FALSE")
    int softDeleteItems(
            @Param("userId") long userId, @Param("compositeDishId") long compositeDishId);

    @Select(
            "SELECT composite_dish_id AS compositeDishId,user_id AS userId,dish_name AS dishName,total_servings AS totalServings,calories_kcal_per_serving AS caloriesKcalPerServing,protein_g_per_serving AS proteinGPerServing,fat_g_per_serving AS fatGPerServing,carbs_g_per_serving AS carbsGPerServing,nutrition_source AS nutritionSource,revision,is_deleted AS deleted,created_at AS createdAt,updated_at AS updatedAt FROM composite_dishes WHERE composite_dish_id=#{compositeDishId} AND user_id=#{userId} AND is_deleted=#{includeDeleted}")
    DishRow findOwned(
            @Param("userId") long userId,
            @Param("compositeDishId") long compositeDishId,
            @Param("includeDeleted") boolean includeDeleted);

    @Select(
            "SELECT composite_dish_id AS compositeDishId,user_id AS userId,dish_name AS dishName,total_servings AS totalServings,calories_kcal_per_serving AS caloriesKcalPerServing,protein_g_per_serving AS proteinGPerServing,fat_g_per_serving AS fatGPerServing,carbs_g_per_serving AS carbsGPerServing,nutrition_source AS nutritionSource,revision,is_deleted AS deleted,created_at AS createdAt,updated_at AS updatedAt FROM composite_dishes WHERE user_id=#{userId} AND is_deleted=FALSE ORDER BY updated_at DESC,composite_dish_id DESC")
    List<DishRow> findOwnedList(@Param("userId") long userId);

    @Select(
            "SELECT composite_dish_item_id AS itemId,item_order AS itemOrder,nutrition_food_id AS nutritionFoodId,raw_name AS rawName,amount,unit,normalized_amount AS normalizedAmount,normalized_unit AS normalizedUnit,conversion_id AS conversionId,calories_kcal AS caloriesKcal,protein_g AS proteinG,fat_g AS fatG,carbs_g AS carbsG FROM composite_dish_items WHERE composite_dish_id=#{compositeDishId} AND is_deleted=FALSE ORDER BY item_order")
    List<ItemSnapshot> findItems(@Param("compositeDishId") long compositeDishId);

    @Update(
            "UPDATE composite_dishes SET is_deleted=TRUE,deleted_at=CURRENT_TIMESTAMP,deleted_by=#{userId},updated_at=CURRENT_TIMESTAMP,updated_by=#{userId},revision=revision+1 WHERE composite_dish_id=#{compositeDishId} AND user_id=#{userId} AND revision=#{revision} AND is_deleted=FALSE")
    int softDelete(
            @Param("userId") long userId,
            @Param("compositeDishId") long compositeDishId,
            @Param("revision") long revision);
}
