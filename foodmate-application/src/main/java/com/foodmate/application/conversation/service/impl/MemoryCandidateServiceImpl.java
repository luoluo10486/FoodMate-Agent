package com.foodmate.application.conversation.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodmate.application.conversation.port.out.MemoryRepository;
import com.foodmate.application.conversation.service.MemoryCandidateService;
import com.foodmate.application.conversation.service.SessionSummaryService;
import com.foodmate.shared.id.IdGenerator;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Java 权威校验并写入长期记忆候选；Python 只能提出候选，不能直接写业务库。 */
@Service
public class MemoryCandidateServiceImpl implements MemoryCandidateService {
    private final MemoryRepository store;
    private final IdGenerator ids;
    private final SessionSummaryService summaries;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    public MemoryCandidateServiceImpl(
            MemoryRepository store, IdGenerator ids, SessionSummaryService summaries) {
        this.store = store;
        this.ids = ids;
        this.summaries = summaries;
    }

    @SuppressWarnings("unchecked")
    @Transactional
    @Override
    public void persistFromCompletedRun(long runId, Map<String, Object> payload) {
        if (payload == null) return;
        List<Map<String, Object>> candidates =
                payload.get("memory_candidates") instanceof List<?> list
                        ? (List<Map<String, Object>>) (List<?>) list
                        : List.of();
        if (candidates.isEmpty()) return;
        Long userId = store.findRunOwner(runId);
        if (userId == null) return;
        boolean persisted = false;
        for (Map<String, Object> candidate : candidates) {
            if (!allowed(candidate)) continue;
            String type = text(candidate.get("memory_type"), 32);
            String key = text(candidate.get("memory_key"), 64);
            String scope = text(candidate.get("scope"), 32);
            BigDecimal confidence = decimal(candidate.get("confidence"));
            if (type == null
                    || key == null
                    || confidence == null
                    || confidence.signum() < 0
                    || confidence.compareTo(BigDecimal.ONE) > 0) continue;
            String candidateJson = json(candidate.get("memory_value"));
            boolean conflict = store.hasDifferentValue(userId, type, key, candidateJson);
            store.insert(
                    new MemoryRepository.NewMemory(
                            ids.nextId(),
                            userId,
                            type,
                            key,
                            candidateJson,
                            confidence,
                            text(candidate.get("source"), 32),
                            scope,
                            conflict ? "conflict" : "confirmed"));
            persisted = true;
        }
        if (persisted) summaries.invalidateForUser(userId);
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
        return get(userId, memoryId);
    }

    /** 逻辑删除记忆，避免破坏运行审计和历史上下文来源。 */
    @Transactional
    @Override
    public void delete(long userId, long memoryId) {
        requireOwned(userId, memoryId);
        store.softDeleteOwned(userId, memoryId);
        summaries.invalidateForUser(userId);
    }

    /** 用户明确确认同一 key 的冲突记忆后，才允许它参与后续 Context 装配。 */
    @Transactional
    @Override
    public MemoryView confirm(long userId, long memoryId) {
        requireOwned(userId, memoryId);
        store.confirmOwned(userId, memoryId);
        summaries.invalidateForUser(userId);
        return get(userId, memoryId);
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

    private boolean allowed(Map<String, Object> candidate) {
        String text = String.valueOf(candidate).toLowerCase();
        // 医疗判断、预算确认和模型推测不能自动进入长期记忆。
        return !text.matches(".*(诊断|处方|疾病|药物|医疗|预算|审批|推测|猜测|diagnos|prescription|medication).*")
                && candidate.get("source_message_ids") instanceof List<?> ids
                && !ids.isEmpty();
    }

    private static String text(Object value, int max) {
        if (value == null || value.toString().isBlank() || value.toString().length() > max)
            return null;
        return value.toString();
    }

    private static BigDecimal decimal(Object value) {
        try {
            return value == null ? null : new BigDecimal(value.toString());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value == null ? Map.of() : value);
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
