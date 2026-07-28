package com.foodmate.application.runtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodmate.shared.id.IdGenerator;
import com.foodmate.shared.runtime.V1RunEvent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 校验并投影 V1 Runtime 事件；网络发送由独立 publisher 负责。 */
@Service
public class V1RuntimeEventService {
    private final JdbcTemplate jdbc;
    private final IdGenerator ids;
    private final AgentAdmissionService admission;
    private final MemoryCandidateService memories;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final Map<String, List<V1RunEvent>> memoryEvents = new HashMap<>();
    private final Map<String, Long> memorySequence = new HashMap<>();

    public V1RuntimeEventService(ObjectProvider<JdbcTemplate> jdbcProvider, IdGenerator ids) {
        this(jdbcProvider, ids, null, null);
    }

    public V1RuntimeEventService(ObjectProvider<JdbcTemplate> jdbcProvider, IdGenerator ids,
                                 ObjectProvider<AgentAdmissionService> admissionProvider) {
        this(jdbcProvider, ids, admissionProvider, null);
    }

    @Autowired
    public V1RuntimeEventService(ObjectProvider<JdbcTemplate> jdbcProvider, IdGenerator ids,
                                 ObjectProvider<AgentAdmissionService> admissionProvider,
                                 ObjectProvider<MemoryCandidateService> memoryProvider) {
        this.jdbc = jdbcProvider.getIfAvailable();
        this.ids = ids;
        this.admission = admissionProvider == null ? null : admissionProvider.getIfAvailable();
        this.memories = memoryProvider == null ? null : memoryProvider.getIfAvailable();
    }

    @Transactional
    public synchronized EventResult accept(V1RunEvent event) {
        // 先做幂等、dispatch fencing 和连续 event_seq 校验，合法事件才允许推进状态。
        if (jdbc == null) return acceptMemory(event);
        long runId = parseRunId(event.runId());
        if (!exists("SELECT 1 FROM agent_runs WHERE agent_run_id=? AND is_deleted=FALSE", runId)) {
            throw new com.foodmate.shared.runtime.RuntimeException("RUNTIME_STATE_CONFLICT", "run does not exist");
        }
        var known = jdbc.query("SELECT request_hash FROM runtime_event_inbox_v2 WHERE agent_run_id=? AND event_id=?",
                (rs, row) -> rs.getString(1), runId, event.eventId());
        if (!known.isEmpty()) {
            if (!known.getFirst().equals(event.requestHash())) throw new com.foodmate.shared.runtime.RuntimeException("RUNTIME_EVENT_IDEMPOTENCY_CONFLICT", "event hash conflict");
            return new EventResult(event.runId(), event.eventId(), true, statusFor(event));
        }
        var dispatch = jdbc.query("SELECT agent_run_dispatch_id,last_event_seq,dispatch_arbitration_state,attempt FROM agent_run_dispatches WHERE agent_run_id=? AND dispatch_id=?",
                (rs, row) -> new DispatchRow(rs.getLong(1), rs.getLong(2), rs.getString(3), rs.getInt(4)), runId, event.dispatchId());
        if (dispatch.isEmpty() || !"active".equals(dispatch.getFirst().state()) || dispatch.getFirst().attempt() != event.attempt()) {
            reject(event, runId, "old_dispatch", "RUNTIME_STATE_CONFLICT");
            throw new com.foodmate.shared.runtime.RuntimeException("RUNTIME_STATE_CONFLICT", "dispatch is not active");
        }
        long expected = dispatch.getFirst().lastEventSeq() + 1;
        if (event.eventSeq() != expected) {
            String code = event.eventSeq() < expected ? "RUNTIME_EVENT_OUT_OF_ORDER" : "RUNTIME_EVENT_GAP";
            reject(event, runId, event.eventSeq() < expected ? "out_of_order" : "gap", code);
            throw new com.foodmate.shared.runtime.RuntimeException(code, "event sequence is not contiguous");
        }
        String status = statusFor(event);
        boolean changesRunStatus = !"unchanged".equals(status);
        String payload = json(event.payload());
        jdbc.update("INSERT INTO runtime_event_inbox_v2(runtime_event_inbox_id,agent_run_id,dispatch_id,event_id,event_seq,event_type,occurred_at,payload_json,request_hash,processing_status,applied_at) VALUES (?,?,?,?,?,?,?,CAST(? AS jsonb),?,'applied',CURRENT_TIMESTAMP)",
                ids.nextId(), runId, event.dispatchId(), event.eventId(), event.eventSeq(), event.eventType(), java.sql.Timestamp.from(event.occurredAt()), payload, event.requestHash());
        if ("run.model_usage".equals(event.eventType())) persistModelUsage(runId, event);
        jdbc.update("UPDATE agent_run_dispatches SET last_event_seq=?,accepted_at=COALESCE(accepted_at,CURRENT_TIMESTAMP),status=CASE WHEN ? IN ('completed','failed','cancelled') THEN 'delivered' ELSE status END,finished_at=CASE WHEN ? IN ('completed','failed','cancelled') THEN CURRENT_TIMESTAMP ELSE finished_at END,updated_at=CURRENT_TIMESTAMP WHERE agent_run_id=? AND dispatch_id=?",
                event.eventSeq(), status, status, runId, event.dispatchId());
        String result = json(event.payload());
        if (changesRunStatus) {
            // 终态后不允许任何状态回退；superseded 等 Java 侧终态同样受保护。
            jdbc.update("UPDATE agent_runs SET status=?,result_json=CASE WHEN ? IN ('completed','validating') THEN CAST(? AS jsonb) ELSE result_json END,error_code=CASE WHEN ?='failed' THEN 'RUNTIME_FAILED' ELSE error_code END,updated_at=CURRENT_TIMESTAMP WHERE agent_run_id=? AND status NOT IN ('completed','failed','cancelled','superseded')",
                    status, status, result, status, runId);
            if ("completed".equals(status)) {
                Object resultType = readPayload(result).get("result_type");
                if ("normal".equals(resultType) || "safety_degraded".equals(resultType)) {
                    jdbc.update("UPDATE agent_runs SET result_type=? WHERE agent_run_id=?", resultType, runId);
                }
                if (memories != null) memories.persistFromCompletedRun(runId, readPayload(result));
            }
        } else {
            jdbc.update("UPDATE agent_runs SET updated_at=CURRENT_TIMESTAMP WHERE agent_run_id=?", runId);
        }
        if ("run.cancel_acknowledged".equals(event.eventType())) {
            jdbc.update("UPDATE agent_run_cancellations SET status='acknowledged',acknowledged_at=COALESCE(acknowledged_at,CURRENT_TIMESTAMP),updated_at=CURRENT_TIMESTAMP WHERE agent_run_id=? AND dispatch_id=? AND status IN ('requested','dispatched')", runId, event.dispatchId());
        } else if ("run.cancelled".equals(event.eventType())) {
            jdbc.update("UPDATE agent_run_cancellations SET status='resolved',acknowledged_at=COALESCE(acknowledged_at,CURRENT_TIMESTAMP),resolved_at=COALESCE(resolved_at,CURRENT_TIMESTAMP),updated_at=CURRENT_TIMESTAMP WHERE agent_run_id=? AND dispatch_id=? AND status IN ('requested','dispatched','acknowledged')", runId, event.dispatchId());
        }
        // AgentRun 行锁保证同一个 run 的 SSE stream_seq 严格递增。
        long streamSeq = jdbc.queryForObject("SELECT sse_last_stream_seq + 1 FROM agent_runs WHERE agent_run_id=? FOR UPDATE", Long.class, runId);
        String sseId = "sse_" + ids.nextId();
        jdbc.update("INSERT INTO agent_run_sse_outbox(agent_run_sse_outbox_id,agent_run_id,sse_event_id,stream_seq,source_event_key,event_type,payload_json) VALUES (?,?,?,?,?,?,CAST(? AS jsonb))",
                ids.nextId(), runId, sseId, streamSeq, event.runId() + ":" + event.eventId(), event.eventType(), payload);
        jdbc.update("UPDATE agent_runs SET sse_last_stream_seq=? WHERE agent_run_id=?", streamSeq, runId);
        if (isTerminal(status) && admission != null) {
            // 终态是释放 Redis lease 的唯一业务触发点；提升结果再回写数据库，Relay 才会继续发送。
            try {
                for (String promotedRunId : admission.releaseAndPromote(event.runId())) {
                    jdbc.update("UPDATE runtime_dispatch_outbox SET status='pending',queued_at=NULL,next_attempt_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE run_id=? AND status='queued'",
                            promotedRunId);
                }
            } catch (com.foodmate.shared.runtime.RuntimeException coordinationFailure) {
                // Redis 故障不能回滚已经接受的终态；lease 到期后由下一轮协调修复。
                if (!"RUNTIME_COORDINATION_UNAVAILABLE".equals(coordinationFailure.code())) throw coordinationFailure;
            }
        }
        return new EventResult(event.runId(), event.eventId(), false, status);
    }

    public synchronized List<V1RunEvent> events(String runId) {
        if (jdbc == null) return List.copyOf(memoryEvents.getOrDefault(runId, List.of()));
        return jdbc.query("SELECT event_id,dispatch_id,attempt,event_seq,event_type,occurred_at,payload_json,request_hash FROM runtime_event_inbox_v2 WHERE agent_run_id=? ORDER BY event_seq",
                (rs, row) -> new V1RunEvent("v1", runId, rs.getString(2), rs.getInt(3), rs.getString(1), rs.getLong(4), "persisted", "persisted", rs.getString(8), rs.getTimestamp(6).toInstant(), rs.getString(5), readPayload(rs.getString(7))), parseRunId(runId));
    }

    public synchronized List<SseRecord> sseEvents(String runId, long afterSequence) {
        if (jdbc == null) return memoryEvents.getOrDefault(runId, List.of()).stream()
                .filter(event -> event.eventSeq() > afterSequence)
                .map(event -> new SseRecord(event.eventSeq(), "sse_memory_" + event.eventId(), event.eventType(), event.payload(), event.eventType().equals("run.completed") || event.eventType().equals("run.failed") || event.eventType().equals("run.cancelled")))
                .toList();
        return jdbc.query("SELECT stream_seq,sse_event_id,event_type,payload_json FROM agent_run_sse_outbox WHERE agent_run_id=? AND stream_seq>? ORDER BY stream_seq",
                (rs, row) -> new SseRecord(rs.getLong(1), rs.getString(2), rs.getString(3), readPayload(rs.getString(4)), terminalType(rs.getString(3))), parseRunId(runId), afterSequence);
    }

    public synchronized long cursorFor(String runId, String cursor) {
        if (cursor == null || cursor.isBlank()) return 0;
        try { return Math.max(0, Long.parseLong(cursor)); }
        catch (NumberFormatException ignored) { }
        if (jdbc == null) return 0;
        List<Long> rows = jdbc.query("SELECT stream_seq FROM agent_run_sse_outbox WHERE agent_run_id=? AND sse_event_id=?", (rs, row) -> rs.getLong(1), parseRunId(runId), cursor);
        return rows.isEmpty() ? 0 : rows.getFirst();
    }

    public synchronized String status(String runId) {
        // agent_runs.status 是唯一权威；superseded 等 Java 侧终态不产生 Python 事件，只能从投影读取。
        if (jdbc != null) {
            List<String> stored = jdbc.query("SELECT status FROM agent_runs WHERE agent_run_id=? AND is_deleted=FALSE",
                    (rs, row) -> rs.getString(1), parseRunId(runId));
            if (!stored.isEmpty()) return stored.getFirst();
        }
        List<V1RunEvent> history = events(runId);
        if (history.isEmpty()) return "queued";
        String status = "queued";
        for (V1RunEvent event : history) {
            String candidate = statusFor(event);
            if (!"unchanged".equals(candidate)) status = candidate;
        }
        return status;
    }

    public void requireRunOwner(String runId, long userId) {
        if (jdbc == null) return;
        long numeric = parseRunId(runId);
        if (!exists("SELECT 1 FROM agent_runs r JOIN sessions s ON s.session_id=r.session_id WHERE r.agent_run_id=? AND r.created_by=? AND r.is_deleted=FALSE AND s.user_id=? AND s.is_deleted=FALSE", numeric, userId, userId)) {
            throw new com.foodmate.shared.error.BusinessException(com.foodmate.shared.error.ErrorCode.FORBIDDEN);
        }
    }

    private EventResult acceptMemory(V1RunEvent event) {
        List<V1RunEvent> current = memoryEvents.computeIfAbsent(event.runId(), ignored -> new ArrayList<>());
        if (current.stream().anyMatch(item -> item.eventId().equals(event.eventId()))) return new EventResult(event.runId(), event.eventId(), true, statusFor(event));
        long expected = memorySequence.getOrDefault(event.runId(), 0L) + 1;
        if (event.eventSeq() != expected) throw new com.foodmate.shared.runtime.RuntimeException(event.eventSeq() < expected ? "RUNTIME_EVENT_OUT_OF_ORDER" : "RUNTIME_EVENT_GAP", "event sequence is not contiguous");
        current.add(event); memorySequence.put(event.runId(), event.eventSeq());
        return new EventResult(event.runId(), event.eventId(), false, statusFor(event));
    }

    private void reject(V1RunEvent event, long runId, String reason, String code) {
        jdbc.update("INSERT INTO runtime_event_rejections(rejection_id,agent_run_id,dispatch_id,attempt,event_id,event_seq,request_hash,reason,error_code,raw_envelope_json) VALUES (?,?,?,?,?,?,?,?,?,CAST(? AS jsonb))",
                "rej_" + ids.nextId(), runId, event.dispatchId(), event.attempt(), event.eventId(), event.eventSeq(), event.requestHash(), reason, code, json(event));
    }

    private boolean exists(String sql, Object... args) { return Boolean.TRUE.equals(jdbc.query(sql, (org.springframework.jdbc.core.ResultSetExtractor<Boolean>) rs -> rs.next(), args)); }
    private long parseRunId(String runId) { try { return Long.parseLong(runId); } catch (NumberFormatException e) { throw new com.foodmate.shared.runtime.RuntimeException("RUNTIME_STATE_CONFLICT", "run id is invalid"); } }
    private String json(Object value) { try { return mapper.writeValueAsString(value); } catch (JsonProcessingException e) { throw new com.foodmate.shared.runtime.RuntimeException("RUNTIME_CONTRACT_INVALID", "payload is not JSON"); } }
    @SuppressWarnings("unchecked") private Map<String, Object> readPayload(String value) { try { return mapper.readValue(value, Map.class); } catch (JsonProcessingException e) { return Map.of(); } }

    private String statusFor(V1RunEvent event) {
        return switch (event.eventType()) {
            case "run.accepted" -> "queued";
            case "run.routed" -> "routed";
            case "run.clarification_requested" -> "waiting_user";
            case "run.planned" -> "planning";
            case "run.retrieval_started", "run.retrieval_finished" -> "retrieving";
            case "run.tool_started", "run.tool_finished" -> "executing";
            case "run.answer_stream" -> "validating";
            case "run.completed" -> "completed";
            case "run.failed" -> "failed";
            case "run.cancelled" -> "cancelled";
            case "run.cancel_acknowledged", "run.model_usage" -> "unchanged";
            default -> throw new com.foodmate.shared.runtime.RuntimeException("RUNTIME_CONTRACT_INVALID", "unsupported event type");
        };
    }

    private record DispatchRow(long id, long lastEventSeq, String state, int attempt) {}
    public record EventResult(String runId, String eventId, boolean duplicate, String status) {}
    public record SseRecord(long streamSeq, String sseEventId, String eventType, Map<String, Object> payload, boolean terminal) {}
    private static boolean terminalType(String type) { return type.equals("run.completed") || type.equals("run.failed") || type.equals("run.cancelled") || type.equals("run.superseded"); }
    private static boolean isTerminal(String status) { return status.equals("completed") || status.equals("failed") || status.equals("cancelled"); }

    private void persistModelUsage(long runId, V1RunEvent event) {
        Map<String, Object> payload = event.payload();
        Map<String, Object> usage = payload.get("usage") instanceof Map<?, ?> value ? (Map<String, Object>) value : Map.of();
        Map<String, Object> cost = payload.get("cost") instanceof Map<?, ?> value ? (Map<String, Object>) value : Map.of();
        Object amount = cost.get("amount");
        jdbc.update("INSERT INTO model_usage_logs(model_usage_log_id,request_id,trace_id,scene,provider_code,model_name,usage_json,latency_ms,cost_amount,status,created_by,updated_by) VALUES (?,?,?,?,?,?,CAST(? AS jsonb),?,?,?,NULL,NULL) ON CONFLICT (request_id) DO NOTHING",
                ids.nextId(), event.requestId(), event.traceId(), payload.get("scene"), payload.get("provider_code"), payload.get("model_name"),
                json(usage), number(payload.get("latency_ms")), amount == null ? null : new java.math.BigDecimal(amount.toString()), payload.getOrDefault("status", "success"));
    }

    private static Integer number(Object value) { try { return value == null ? null : Integer.valueOf(value.toString()); } catch (NumberFormatException ignored) { return null; } }
}
