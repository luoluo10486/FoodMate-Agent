package com.foodmate.application.conversation.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** 长期记忆候选的校验和管理服务。 */
public interface MemoryCandidateService {
    void persistFromCompletedRun(long runId, CompletedRunPayload payload);

    List<MemoryView> list(long userId);

    MemoryView update(long userId, long memoryId, String memoryValue, String scope);

    void delete(long userId, long memoryId);

    MemoryView confirm(long userId, long memoryId);

    record CompletedRunPayload(List<MemoryCandidate> memoryCandidates) {
        public CompletedRunPayload {
            memoryCandidates = memoryCandidates == null ? List.of() : List.copyOf(memoryCandidates);
        }
    }

    record MemoryCandidate(
            String memoryType,
            String memoryKey,
            JsonNode memoryValue,
            BigDecimal confidence,
            String source,
            String scope,
            List<String> sourceMessageIds) {
        public MemoryCandidate {
            sourceMessageIds = sourceMessageIds == null ? List.of() : List.copyOf(sourceMessageIds);
        }
    }

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
