package com.foodmate.application.conversation.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodmate.application.common.service.OperationAuditService;
import com.foodmate.application.conversation.port.out.MemoryRepository;
import com.foodmate.application.conversation.service.MemoryCandidateService;
import com.foodmate.application.conversation.service.SessionSummaryService;
import com.foodmate.shared.id.IdGenerator;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Java 权威校验并写入长期记忆候选；Python 只能提出候选，不能直接写业务库。 */
@Service
public class MemoryCandidateServiceImpl implements MemoryCandidateService {
    private final MemoryRepository store;
    private final IdGenerator ids;
    private final SessionSummaryService summaries;
    private final OperationAuditService audit;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    public MemoryCandidateServiceImpl(
            MemoryRepository store, IdGenerator ids, SessionSummaryService summaries) {
        this(store, ids, summaries, null);
    }

    @Autowired
    public MemoryCandidateServiceImpl(
            MemoryRepository store,
            IdGenerator ids,
            SessionSummaryService summaries,
            ObjectProvider<OperationAuditService> auditProvider) {
        this.store = store;
        this.ids = ids;
        this.summaries = summaries;
        this.audit = auditProvider == null ? null : auditProvider.getIfAvailable();
    }

    @Transactional
    @Override
    public void persistFromCompletedRun(long runId, CompletedRunPayload payload) {
        if (payload == null) return;
        List<MemoryCandidate> candidates = payload.memoryCandidates();
        if (candidates.isEmpty()) return;
        Long userId = store.findRunOwner(runId);
        if (userId == null) return;
        boolean persisted = false;
        for (MemoryCandidate candidate : candidates) {
            if (!allowed(candidate)) continue;
            String type = text(candidate.memoryType(), 32);
            String key = text(candidate.memoryKey(), 64);
            String scope = text(candidate.scope(), 32);
            BigDecimal confidence = candidate.confidence();
            if (type == null
                    || key == null
                    || confidence == null
                    || confidence.signum() < 0
                    || confidence.compareTo(BigDecimal.ONE) > 0) continue;
            String candidateJson = json(candidate.memoryValue());
            boolean conflict = store.hasDifferentValue(userId, type, key, candidateJson);
            store.insert(
                    new MemoryRepository.NewMemory(
                            ids.nextId(),
                            userId,
                            type,
                            key,
                            candidateJson,
                            confidence,
                            text(candidate.source(), 32),
                            scope,
                            conflict ? "conflict" : "confirmed"));
            persisted = true;
        }
        if (persisted) {
            summaries.invalidateForUser(userId);
            record(
                    userId,
                    "memory",
                    Long.toString(runId),
                    "memory.candidate.persist",
                    "success",
                    null,
                    Map.of("candidate_count", candidates.size()));
        }
    }

    /** 查询用户可见的长期记忆；过期和逻辑删除记录不会重新进入 Agent Context。 */
    @Override
    public List<MemoryView> list(long userId) {
        return store.findVisible(userId, 100).stream()
                .map(MemoryCandidateServiceImpl::view)
                .toList();
    }

    /** 用户只能修改自己的记忆，修改后保留来源和审计归属。 */
    @Transactional
    @Override
    public MemoryView update(long userId, long memoryId, String memoryValue, String scope) {
        requireOwned(userId, memoryId);
        int changed =
                store.updateOwned(
                        userId, memoryId, memoryValue == null ? "{}" : memoryValue, scope);
        if (changed != 1) throw new IllegalArgumentException("memory not found");
        summaries.invalidateForUser(userId);
        record(
                userId,
                "memory",
                Long.toString(memoryId),
                "memory.update",
                "success",
                null,
                Map.of("scope", scope == null ? "" : scope));
        return get(userId, memoryId);
    }

    /** 逻辑删除记忆，避免破坏运行审计和历史上下文来源。 */
    @Transactional
    @Override
    public void delete(long userId, long memoryId) {
        requireOwned(userId, memoryId);
        store.softDeleteOwned(userId, memoryId);
        summaries.invalidateForUser(userId);
        record(
                userId,
                "memory",
                Long.toString(memoryId),
                "memory.delete",
                "success",
                null,
                Map.of());
    }

    /** 用户明确确认同一 key 的冲突记忆后，才允许它参与后续 Context 装配。 */
    @Transactional
    @Override
    public MemoryView confirm(long userId, long memoryId) {
        requireOwned(userId, memoryId);
        store.confirmOwned(userId, memoryId);
        summaries.invalidateForUser(userId);
        record(
                userId,
                "memory",
                Long.toString(memoryId),
                "memory.confirm",
                "success",
                null,
                Map.of());
        return get(userId, memoryId);
    }

    private void record(
            long userId,
            String targetType,
            String targetId,
            String action,
            String result,
            String errorCode,
            Map<String, ?> metadata) {
        if (audit != null)
            audit.record(
                    userId, targetType, targetId, action, result, errorCode, null, null, metadata);
    }

    private MemoryView get(long userId, long memoryId) {
        MemoryRepository.MemorySnapshot memory = store.findOwned(userId, memoryId);
        if (memory == null) throw new IllegalArgumentException("memory not found");
        return view(memory);
    }

    private void requireOwned(long userId, long memoryId) {
        if (!store.existsOwned(userId, memoryId)) {
            throw new IllegalArgumentException("memory not found");
        }
    }

    private boolean allowed(MemoryCandidate candidate) {
        String text = String.valueOf(candidate).toLowerCase();
        // 医疗判断、预算确认和模型推测不能自动进入长期记忆。
        return !text.matches(".*(诊断|处方|疾病|药物|医疗|预算|审批|推测|猜测|diagnos|prescription|medication).*")
                && !candidate.sourceMessageIds().isEmpty();
    }

    private static String text(Object value, int max) {
        if (value == null || value.toString().isBlank() || value.toString().length() > max)
            return null;
        return value.toString();
    }

    private String json(JsonNode value) {
        try {
            return mapper.writeValueAsString(value == null ? mapper.createObjectNode() : value);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private static MemoryView view(MemoryRepository.MemorySnapshot value) {
        return new MemoryView(
                value.memoryId(),
                value.memoryType(),
                value.memoryKey(),
                value.memoryValue(),
                value.confidence(),
                value.source(),
                value.scope(),
                value.confirmationStatus(),
                value.expiresAt(),
                value.updatedAt());
    }
}
