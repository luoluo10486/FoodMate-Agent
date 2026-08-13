package com.foodmate.infrastructure.persistence.food.adapter;

import com.foodmate.application.food.port.out.FoodLogRepository;
import com.foodmate.infrastructure.persistence.food.FoodLogMapper;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

/** 将饮食记录 MyBatis 映射暴露为 application 端口。 */
@Repository
@Profile("local")
public class FoodLogRepositoryAdapter implements FoodLogRepository {
    private final FoodLogMapper mapper;

    public FoodLogRepositoryAdapter(FoodLogMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public boolean sessionOwned(long userId, long sessionId) {
        return mapper.sessionOwned(userId, sessionId);
    }

    @Override
    public boolean agentRunOwned(long userId, long agentRunId) {
        return mapper.agentRunOwned(userId, agentRunId);
    }

    @Override
    public NutritionFoodLookup findNutritionFood(String normalizedName) {
        return mapper.findNutritionFood(normalizedName);
    }

    @Override
    public UnitConversionLookup findUnitConversion(
            long nutritionFoodId, String sourceUnit, String targetUnit) {
        return mapper.findUnitConversion(nutritionFoodId, sourceUnit, targetUnit);
    }

    @Override
    public IdempotencyRecord findIdempotency(long userId, String idempotencyKey) {
        return mapper.findIdempotency(userId, idempotencyKey);
    }

    @Override
    public int insertFoodLog(FoodLogWrite write) {
        return mapper.insertFoodLog(write);
    }

    @Override
    public void insertItem(FoodLogItemWrite item) {
        mapper.insertItem(item);
    }

    @Override
    public int softDelete(long userId, long foodLogId, long revision) {
        return mapper.softDelete(userId, foodLogId, revision);
    }

    @Override
    public int restore(long userId, long foodLogId, long revision) {
        return mapper.restore(userId, foodLogId, revision);
    }

    @Override
    public int reserveAudit(AuditWrite audit) {
        return mapper.reserveAudit(audit);
    }

    @Override
    public int completeAudit(long operatorId, String idempotencyKey, String responseJson) {
        return mapper.completeAudit(operatorId, idempotencyKey, responseJson);
    }

    @Override
    public List<FoodLogSnapshot> findVisible(
            long userId, java.time.Instant from, java.time.Instant to) {
        return mapper.findVisible(userId, from, to).stream().map(this::withItems).toList();
    }

    @Override
    public FoodLogSnapshot findOwned(long userId, long foodLogId, boolean includeDeleted) {
        FoodLogMapper.FoodLogRow row = mapper.findOwned(userId, foodLogId, includeDeleted);
        return row == null ? null : withItems(row);
    }

    private FoodLogSnapshot withItems(FoodLogMapper.FoodLogRow row) {
        return new FoodLogSnapshot(
                row.foodLogId(),
                row.userId(),
                row.sessionId(),
                row.agentRunId(),
                row.mealTime(),
                row.mealType(),
                row.notes(),
                row.source(),
                row.revision(),
                row.deleted(),
                row.createdAt(),
                row.updatedAt(),
                mapper.findItems(row.foodLogId()));
    }
}
