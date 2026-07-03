package com.foodmate.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import java.time.Instant;
import java.util.Objects;

public abstract class SoftDeleteRepositorySupport<T extends BasePo> {
    private final BaseMapper<T> mapper;

    protected SoftDeleteRepositorySupport(BaseMapper<T> mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper must not be null");
    }

    protected int softDelete(T entity, Long operatorId, Instant deletedAt) {
        entity.markDeleted(operatorId, deletedAt);
        return mapper.updateById(entity);
    }

    protected int restore(T entity, RestoreCommand command) {
        entity.restore(command.operatorId());
        return mapper.updateById(entity);
    }

    protected BaseMapper<T> mapper() {
        return mapper;
    }
}
