package com.foodmate.infrastructure.audit;

import java.time.Instant;

public record AuditOperation(
        Long operatorId,
        String requestId,
        String traceId,
        String targetType,
        String targetId,
        String action,
        Instant createdAt
) {
}

