package com.foodmate.application.runtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodmate.shared.id.IdGenerator;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Java 权威校验并写入长期记忆候选；Python 只能提出候选，不能直接写业务库。 */
@Service
public class MemoryCandidateService {
    private final JdbcTemplate jdbc;
    private final IdGenerator ids;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    public MemoryCandidateService(ObjectProvider<JdbcTemplate> provider, IdGenerator ids) {
        this.jdbc = provider.getIfAvailable();
        this.ids = ids;
    }

    @SuppressWarnings("unchecked")
    public void persistFromCompletedRun(long runId, Map<String, Object> payload) {
        if (jdbc == null || payload == null) return;
        List<Map<String, Object>> candidates = payload.get("memory_candidates") instanceof List<?> list
                ? (List<Map<String, Object>>) (List<?>) list : List.of();
        if (candidates.isEmpty()) return;
        Long userId = jdbc.query("SELECT s.user_id FROM agent_runs r JOIN sessions s ON s.session_id=r.session_id WHERE r.agent_run_id=? AND r.is_deleted=FALSE AND s.is_deleted=FALSE",
                rs -> rs.next() ? rs.getLong(1) : null, runId);
        if (userId == null) return;
        for (Map<String, Object> candidate : candidates) {
            if (!allowed(candidate)) continue;
            String type = text(candidate.get("memory_type"), 32);
            String key = text(candidate.get("memory_key"), 64);
            String scope = text(candidate.get("scope"), 32);
            BigDecimal confidence = decimal(candidate.get("confidence"));
            if (type == null || key == null || confidence == null || confidence.signum() < 0 || confidence.compareTo(BigDecimal.ONE) > 0) continue;
            String candidateJson = json(candidate.get("memory_value"));
            boolean conflict = Boolean.TRUE.equals(jdbc.query("SELECT EXISTS(SELECT 1 FROM user_memories WHERE user_id=? AND memory_type=? AND memory_key=? AND is_deleted=FALSE AND memory_value::text<>CAST(? AS jsonb)::text)",
                    rs -> rs.next() && rs.getBoolean(1), userId, type, key, candidateJson));
            jdbc.update("INSERT INTO user_memories(memory_id,user_id,memory_type,memory_key,memory_value,confidence,source,scope,confirmation_status,expires_at,created_by,updated_by) VALUES (?,?,?,?,CAST(? AS jsonb),?,?,?,?,NULL,?,?)",
                    ids.nextId(), userId, type, key, candidateJson, confidence,
                    text(candidate.get("source"), 32), scope, conflict ? "conflict" : "confirmed", userId, userId);
        }
    }

    /** 查询用户可见的长期记忆；过期和逻辑删除记录不会重新进入 Agent Context。 */
    public List<MemoryView> list(long userId) {
        if (jdbc == null) return List.of();
        return jdbc.query("SELECT memory_id,memory_type,memory_key,memory_value::text,confidence,source,scope,confirmation_status,expires_at,updated_at FROM user_memories WHERE user_id=? AND is_deleted=FALSE AND (expires_at IS NULL OR expires_at>CURRENT_TIMESTAMP) ORDER BY updated_at DESC LIMIT 100",
                (rs, row) -> new MemoryView(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getBigDecimal(5), rs.getString(6), rs.getString(7), rs.getString(8), rs.getTimestamp(9) == null ? null : rs.getTimestamp(9).toInstant(), rs.getTimestamp(10).toInstant()), userId);
    }

    /** 用户只能修改自己的记忆，修改后保留来源和审计归属。 */
    @Transactional
    public MemoryView update(long userId, long memoryId, String memoryValue, String scope) {
        requireOwned(userId, memoryId);
        int changed = jdbc.update("UPDATE user_memories SET memory_value=CAST(? AS jsonb),scope=COALESCE(NULLIF(?,''),scope),confirmation_status='confirmed',updated_at=CURRENT_TIMESTAMP,updated_by=? WHERE memory_id=? AND user_id=? AND is_deleted=FALSE",
                memoryValue == null ? "{}" : memoryValue, scope, userId, memoryId, userId);
        if (changed != 1) throw new IllegalArgumentException("memory not found");
        return get(userId, memoryId);
    }

    /** 逻辑删除记忆，避免破坏运行审计和历史上下文来源。 */
    @Transactional
    public void delete(long userId, long memoryId) {
        requireOwned(userId, memoryId);
        jdbc.update("UPDATE user_memories SET is_deleted=TRUE,deleted_at=CURRENT_TIMESTAMP,deleted_by=?,updated_at=CURRENT_TIMESTAMP,updated_by=? WHERE memory_id=? AND user_id=? AND is_deleted=FALSE",
                userId, userId, memoryId, userId);
    }

    /** 用户明确确认同一 key 的冲突记忆后，才允许它参与后续 Context 装配。 */
    @Transactional
    public MemoryView confirm(long userId, long memoryId) {
        requireOwned(userId, memoryId);
        jdbc.update("UPDATE user_memories SET confirmation_status='confirmed',updated_at=CURRENT_TIMESTAMP,updated_by=? WHERE memory_id=? AND user_id=? AND is_deleted=FALSE",
                userId, memoryId, userId);
        return get(userId, memoryId);
    }

    private MemoryView get(long userId, long memoryId) {
        return jdbc.query("SELECT memory_id,memory_type,memory_key,memory_value::text,confidence,source,scope,confirmation_status,expires_at,updated_at FROM user_memories WHERE memory_id=? AND user_id=? AND is_deleted=FALSE",
                (rs, row) -> new MemoryView(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getBigDecimal(5), rs.getString(6), rs.getString(7), rs.getString(8), rs.getTimestamp(9) == null ? null : rs.getTimestamp(9).toInstant(), rs.getTimestamp(10).toInstant()), memoryId, userId)
                .stream().findFirst().orElseThrow(() -> new IllegalArgumentException("memory not found"));
    }

    private void requireOwned(long userId, long memoryId) {
        if (jdbc == null || !Boolean.TRUE.equals(jdbc.query("SELECT EXISTS(SELECT 1 FROM user_memories WHERE memory_id=? AND user_id=? AND is_deleted=FALSE)", rs -> rs.next() && rs.getBoolean(1), memoryId, userId))) {
            throw new IllegalArgumentException("memory not found");
        }
    }

    private boolean allowed(Map<String, Object> candidate) {
        String text = String.valueOf(candidate).toLowerCase();
        // 医疗判断、预算确认和模型推测不能自动进入长期记忆。
        return !text.matches(".*(诊断|处方|疾病|药物|医疗|预算|审批|推测|猜测|diagnos|prescription|medication).*" )
                && candidate.get("source_message_ids") instanceof List<?> ids && !ids.isEmpty();
    }

    private static String text(Object value, int max) {
        if (value == null || value.toString().isBlank() || value.toString().length() > max) return null;
        return value.toString();
    }
    private static BigDecimal decimal(Object value) { try { return value == null ? null : new BigDecimal(value.toString()); } catch (NumberFormatException ignored) { return null; } }
    private String json(Object value) { try { return mapper.writeValueAsString(value == null ? Map.of() : value); } catch (JsonProcessingException e) { return "{}"; } }

    public record MemoryView(long memoryId, String memoryType, String memoryKey, String memoryValue,
                             BigDecimal confidence, String source, String scope, String confirmationStatus,
                             java.time.Instant expiresAt, java.time.Instant updatedAt) {}
}
