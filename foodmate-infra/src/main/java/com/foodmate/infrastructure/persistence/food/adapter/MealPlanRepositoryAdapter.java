package com.foodmate.infrastructure.persistence.food.adapter;

import com.foodmate.application.common.port.out.OperationAuditPort;
import com.foodmate.application.food.port.out.MealPlanRepository;
import com.foodmate.infrastructure.persistence.food.MealPlanMapper;
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
    public int reserveAudit(AuditWrite audit) {
        return this.audit.reserve(
                new OperationAuditPort.AuditRecord(
                        audit.operationAuditId(),
                        audit.operatorId(),
                        audit.requestId(),
                        audit.traceId(),
                        audit.targetType(),
                        audit.targetId(),
                        audit.action(),
                        "pending",
                        null,
                        "{}",
                        audit.responseJson(),
                        audit.parametersDigest(),
                        audit.idempotencyKey()));
    }

    @Override
    public int completeAudit(long operatorId, String idempotencyKey, String responseJson) {
        return audit.complete(operatorId, idempotencyKey, responseJson);
    }
}
