package com.foodmate.infrastructure.persistence;

import java.time.Instant;

public interface SoftDeleteSupport {
    void markDeleted(Long operatorId, Instant deletedAt);

    void restore(Long operatorId);
}

