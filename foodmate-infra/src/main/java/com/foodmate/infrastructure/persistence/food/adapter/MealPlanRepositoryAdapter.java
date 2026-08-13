package com.foodmate.infrastructure.persistence.food.adapter;

import com.foodmate.application.food.port.out.MealPlanRepository;
import com.foodmate.infrastructure.persistence.food.MealPlanMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

/** 将餐食计划 MyBatis 映射暴露为 application 端口。 */
@Repository
@Profile("local")
public class MealPlanRepositoryAdapter implements MealPlanRepository {
    private final MealPlanMapper mapper;

    public MealPlanRepositoryAdapter(MealPlanMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public boolean sessionOwned(long userId, long sessionId) {
        return mapper.sessionOwned(userId, sessionId);
    }

    @Override
    public int insertPlan(PlanWrite plan) {
        return mapper.insertPlan(plan);
    }

    @Override
    public int updatePlanStatus(
            long userId, long mealPlanId, String status, String validationJson) {
        return mapper.updatePlanStatus(userId, mealPlanId, status, validationJson);
    }

    @Override
    public PlanSnapshot findOwnedPlan(long userId, long mealPlanId) {
        return mapper.findOwnedPlan(userId, mealPlanId);
    }

    @Override
    public int insertShoppingList(ShoppingListWrite list) {
        return mapper.insertShoppingList(list);
    }

    @Override
    public ShoppingListSnapshot findOwnedShoppingList(long userId, long mealPlanId) {
        return mapper.findOwnedShoppingList(userId, mealPlanId);
    }
}
