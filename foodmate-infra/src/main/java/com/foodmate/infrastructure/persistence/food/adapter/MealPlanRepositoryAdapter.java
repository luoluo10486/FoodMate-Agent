package com.foodmate.infrastructure.persistence.food.adapter;

import com.foodmate.application.common.port.out.OperationAuditPort;
import com.foodmate.application.food.port.out.MealPlanRepository;
import com.foodmate.infrastructure.persistence.food.MealPlanMapper;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

/** 将餐食计划 MyBatis 映射暴露为 application 端口。 */
@Repository
@Profile("local")
public class MealPlanRepositoryAdapter implements MealPlanRepository {
    private final MealPlanMapper mapper;
    private final OperationAuditPort audit;

    public MealPlanRepositoryAdapter(MealPlanMapper mapper, OperationAuditPort audit) {
        this.mapper = mapper;
        this.audit = audit;
    }

    @Override
    public boolean sessionOwned(long userId, long sessionId) {
        return mapper.sessionOwned(userId, sessionId);
    }

    @Override
    public IdempotencyRecord findIdempotency(long userId, String idempotencyKey) {
        OperationAuditPort.IdempotencyRecord record = audit.findIdempotency(userId, idempotencyKey);
        return record == null
                ? null
                : new IdempotencyRecord(
                        record.parametersDigest(), record.result(), record.responseJson());
    }

    @Override
    public int insertPlan(PlanWrite plan) {
        return mapper.insertPlan(plan);
    }

    @Override
    public int updatePlanStatus(
            long userId,
            long mealPlanId,
            long expectedRevision,
            String status,
            String validationJson) {
        return mapper.updatePlanStatus(
                userId, mealPlanId, expectedRevision, status, validationJson);
    }

    @Override
    public int updatePlanStatus(
            long userId, long mealPlanId, String status, String validationJson) {
        return mapper.updatePlanStatusLegacy(userId, mealPlanId, status, validationJson);
    }

    @Override
    public int updatePlan(UpdatePlanWrite plan) {
        return mapper.updatePlan(plan);
    }

    @Override
    public PlanSnapshot findOwnedPlan(long userId, long mealPlanId, boolean includeDeleted) {
        return mapper.findOwnedPlan(userId, mealPlanId, includeDeleted);
    }

    @Override
    public PlanSnapshot findOwnedPlan(long userId, long mealPlanId) {
        return mapper.findOwnedPlan(userId, mealPlanId, false);
    }

    @Override
    public List<PlanSnapshot> findOwnedPlans(long userId, boolean includeDeleted) {
        return mapper.findOwnedPlans(userId, includeDeleted);
    }

    @Override
    public int softDelete(long userId, long mealPlanId, long revision) {
        return mapper.softDelete(userId, mealPlanId, revision);
    }

    @Override
    public int restore(long userId, long mealPlanId, long revision) {
        return mapper.restore(userId, mealPlanId, revision);
    }

    @Override
    public int softDeleteShoppingList(long userId, long mealPlanId) {
        return mapper.softDeleteShoppingList(userId, mealPlanId);
    }

    @Override
    public int insertShoppingList(ShoppingListWrite list) {
        return mapper.insertShoppingList(list);
    }

    @Override
    public ShoppingListSnapshot findOwnedShoppingList(long userId, long mealPlanId) {
        return mapper.findOwnedShoppingList(userId, mealPlanId);
    }

    @Override
    public ShoppingListSnapshot findLatestShoppingList(long userId, long mealPlanId) {
        return mapper.findLatestShoppingList(userId, mealPlanId);
    }

    @Override
    public int updateShoppingListItems(long userId, long shoppingListId, String itemsJson) {
        return mapper.updateShoppingListItems(userId, shoppingListId, itemsJson);
    }

    @Override
    public MealSlotSnapshot upsertMealSlot(MealSlotWrite slot) {
        mapper.upsertMealSlot(slot);
        return mapper.findMealSlots(slot.userId(), slot.mealPlanId()).stream()
                .filter(
                        value ->
                                value.dayIndex() == slot.dayIndex()
                                        && value.mealType().equals(slot.mealType()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("餐次写入后无法读取"));
    }

    @Override
    public List<MealSlotSnapshot> findMealSlots(long userId, long mealPlanId) {
        return mapper.findMealSlots(userId, mealPlanId);
    }

    @Override
    public ShoppingItemSnapshot upsertShoppingItem(ShoppingItemWrite item) {
        mapper.upsertShoppingItem(item);
        return mapper
                .findShoppingItems(item.userId(), item.mealPlanId(), item.shoppingListId())
                .stream()
                .filter(value -> value.itemKey().equals(item.itemKey()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("购物项写入后无法读取"));
    }

    @Override
    public List<ShoppingItemSnapshot> findShoppingItems(
            long userId, long mealPlanId, long shoppingListId) {
        return mapper.findShoppingItems(userId, mealPlanId, shoppingListId);
    }

    @Override
    public int softDeleteShoppingItemsNotInKeys(
            long userId, long shoppingListId, List<String> activeItemKeys) {
        return mapper.softDeleteShoppingItemsNotInKeys(userId, shoppingListId, activeItemKeys);
    }

    @Override
    public int updateShoppingItemPurchased(
            long userId, long shoppingListItemId, boolean purchased) {
        return mapper.updateShoppingItemPurchased(userId, shoppingListItemId, purchased);
    }

    @Override
    public int deactivateMealSlots(long userId, long mealPlanId) {
        return mapper.deactivateMealSlots(userId, mealPlanId);
    }

    @Override
    public int softDeleteMealSlotsNotInKeys(
            long userId, long mealPlanId, List<String> activeSlotKeys) {
        return mapper.softDeleteMealSlotsNotInKeys(userId, mealPlanId, activeSlotKeys);
    }
}
