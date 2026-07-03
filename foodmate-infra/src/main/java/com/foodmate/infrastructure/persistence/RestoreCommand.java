package com.foodmate.infrastructure.persistence;

public record RestoreCommand(
        String targetType,
        Long targetId,
        Long operatorId,
        String requestId,
        String traceId
) {
}

