package com.foodmate.infrastructure.persistence;

import java.time.Instant;

/**
 * 软删除实体应实现的能力接口。
 */
public interface SoftDeleteSupport {
    void markDeleted(Long operatorId, Instant deletedAt);

    void restore(Long operatorId);
}
