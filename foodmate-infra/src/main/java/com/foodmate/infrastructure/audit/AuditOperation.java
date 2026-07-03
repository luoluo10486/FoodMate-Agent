package com.foodmate.infrastructure.audit;

import java.time.Instant;

/**
 * 审计操作的结构化描述。
 */
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
