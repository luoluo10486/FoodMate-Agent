package com.foodmate.infrastructure.persistence.food.adapter;

import com.foodmate.application.common.port.out.OperationAuditPort;
import com.foodmate.application.food.port.out.CompositeDishRepository;
import com.foodmate.infrastructure.persistence.food.CompositeDishMapper;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

/** 将复合菜 MyBatis 映射暴露为 application 端口。 */
@Repository
@Profile("local")
public class CompositeDishRepositoryAdapter implements CompositeDishRepository {
    private final CompositeDishMapper mapper;
    private final OperationAuditPort audit;

    public CompositeDishRepositoryAdapter(CompositeDishMapper mapper, OperationAuditPort audit) {
        this.mapper = mapper;
        this.audit = audit;
    }

    @Override
    public IdempotencyRecord findIdempotency(long userId, String idempotencyKey) {
        OperationAuditPort.IdempotencyRecord value = audit.findIdempotency(userId, idempotencyKey);
        return value == null
                ? null
                : new IdempotencyRecord(
                        value.parametersDigest(), value.result(), value.responseJson());
    }

    @Override
    public int insertDish(DishWrite write) {
        return mapper.insertDish(write);
    }

    @Override
    public int updateDish(UpdateDishWrite write) {
        return mapper.updateDish(write);
    }

    @Override
    public void insertItem(ItemWrite write) {
        mapper.insertItem(write);
    }

    @Override
    public int softDeleteItems(long userId, long compositeDishId) {
        return mapper.softDeleteItems(userId, compositeDishId);
    }

    @Override
    public DishSnapshot findOwned(long userId, long compositeDishId, boolean includeDeleted) {
        CompositeDishMapper.DishRow row = mapper.findOwned(userId, compositeDishId, includeDeleted);
        return row == null ? null : withItems(row);
    }

    @Override
    public List<DishSnapshot> findOwnedList(long userId) {
        return mapper.findOwnedList(userId).stream().map(this::withItems).toList();
    }

    @Override
    public int softDelete(long userId, long compositeDishId, long revision) {
        return mapper.softDelete(userId, compositeDishId, revision);
    }

    private DishSnapshot withItems(CompositeDishMapper.DishRow row) {
        return new DishSnapshot(
                row.compositeDishId(),
                row.userId(),
                row.dishName(),
                row.totalServings(),
                row.caloriesKcalPerServing(),
                row.proteinGPerServing(),
                row.fatGPerServing(),
                row.carbsGPerServing(),
                row.nutritionSource(),
                row.revision(),
                row.deleted(),
                row.createdAt(),
                row.updatedAt(),
                mapper.findItems(row.compositeDishId()));
    }
}
