package com.foodmate.application.conversation.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Validates and manages long-term memory candidates. */
public interface MemoryCandidateService {
    void persistFromCompletedRun(long runId, Map<String, Object> payload);

    List<MemoryView> list(long userId);

    MemoryView update(long userId, long memoryId, String memoryValue, String scope);

    void delete(long userId, long memoryId);

    MemoryView confirm(long userId, long memoryId);

    record MemoryView(
            long memoryId,
            String memoryType,
            String memoryKey,
            String memoryValue,
            BigDecimal confidence,
            String source,
            String scope,
            String confirmationStatus,
            Instant expiresAt,
            Instant updatedAt) {}
}
