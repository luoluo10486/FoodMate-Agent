package com.foodmate.infrastructure.persistence;

/**
 * 软删除恢复命令。
 */
public record RestoreCommand(
        String targetType,
        Long targetId,
        Long operatorId,
        String requestId,
        String traceId
) {
}
