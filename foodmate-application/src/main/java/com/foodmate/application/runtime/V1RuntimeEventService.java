package com.foodmate.application.runtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodmate.application.runtime.persistence.V1RuntimeEventStore;
import com.foodmate.application.runtime.persistence.V1RuntimeEventStore.*;
import com.foodmate.shared.id.IdGenerator;
import com.foodmate.shared.runtime.V1RunEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 校验并投影 V1 Runtime 事件；网络发送由独立 publisher 负责。 */
@Service
public class V1RuntimeEventService {
    private final V1RuntimeEventStore store;
    private final IdGenerator ids;
    private final AgentAdmissionService admission;
    private final MemoryCandidateService memories;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final Map<String, List<V1RunEvent>> memoryEvents = new HashMap<>();
    private final Map<String, Long> memorySequence = new HashMap<>();

    public V1RuntimeEventService(
            ObjectProvider<V1RuntimeEventStore> storeProvider, IdGenerator ids) {
        this(storeProvider, ids, null, null);
    }

    public V1RuntimeEventService(
            ObjectProvider<V1RuntimeEventStore> storeProvider,
            IdGenerator ids,
            ObjectProvider<AgentAdmissionService> admissionProvider) {
        this(storeProvider, ids, admissionProvider, null);
    }

    @Autowired
    public V1RuntimeEventService(
            ObjectProvider<V1RuntimeEventStore> storeProvider,
            IdGenerator ids,
            ObjectProvider<AgentAdmissionService> admissionProvider,
            ObjectProvider<MemoryCandidateService> memoryProvider) {
        this.store = storeProvider.getIfAvailable();
        this.ids = ids;
        this.admission = admissionProvider == null ? null : admissionProvider.getIfAvailable();
        this.memories = memoryProvider == null ? null : memoryProvider.getIfAvailable();
    }

    @Transactional
    public synchronized EventResult accept(V1RunEvent event) {
        // 先做幂等、dispatch fencing 和连续 event_seq 校验，合法事件才允许推进状态。
        if (store == null) return acceptMemory(event);
        long runId = parseRunId(event.runId());
        if (!store.runExists(runId)) {
            throw new com.foodmate.shared.runtime.RuntimeException(
                    "RUNTIME_STATE_CONFLICT", "run does not exist");
        }
        String known = store.eventHash(runId, event.eventId());
        if (known != null) {
            if (!known.equals(event.requestHash()))
                throw new com.foodmate.shared.runtime.RuntimeException(
                        "RUNTIME_EVENT_IDEMPOTENCY_CONFLICT", "event hash conflict");
            return new EventResult(event.runId(), event.eventId(), true, statusFor(event));
        }
        DispatchRow dispatch = store.dispatch(runId, event.dispatchId());
        if (dispatch == null
                || !"active".equals(dispatch.state())
                || dispatch.attempt() != event.attempt()) {
            reject(event, runId, "old_dispatch", "RUNTIME_STATE_CONFLICT");
            throw new com.foodmate.shared.runtime.RuntimeException(
                    "RUNTIME_STATE_CONFLICT", "dispatch is not active");
        }
        long expected = dispatch.lastEventSeq() + 1;
        if (event.eventSeq() != expected) {
            String code =
                    event.eventSeq() < expected
                            ? "RUNTIME_EVENT_OUT_OF_ORDER"
                            : "RUNTIME_EVENT_GAP";
            reject(event, runId, event.eventSeq() < expected ? "out_of_order" : "gap", code);
            throw new com.foodmate.shared.runtime.RuntimeException(
                    code, "event sequence is not contiguous");
        }
        String status = statusFor(event);
        boolean changesRunStatus = !"unchanged".equals(status);
        String payload = json(event.payload());
        store.insertEvent(ids.nextId(), runId, event, payload);
        if ("run.model_usage".equals(event.eventType())) persistModelUsage(runId, event);
        store.updateDispatch(runId, event.dispatchId(), event.eventSeq(), status);
        String result = json(event.payload());
        if (changesRunStatus) {
            // 终态后不允许任何状态回退；superseded 等 Java 侧终态同样受保护。
            store.updateRun(runId, status, result);
            if ("completed".equals(status)) {
                Object resultType = readPayload(result).get("result_type");
                if ("normal".equals(resultType) || "safety_degraded".equals(resultType)) {
                    store.setResultType(runId, resultType.toString());
                }
                persistAssistantMessage(runId, readPayload(result));
                if (memories != null) memories.persistFromCompletedRun(runId, readPayload(result));
            }
        } else {
            store.touchRun(runId);
        }
        if ("run.cancel_acknowledged".equals(event.eventType())) {
            store.acknowledgeCancel(runId, event.dispatchId());
        } else if ("run.cancelled".equals(event.eventType())) {
            store.resolveCancel(runId, event.dispatchId());
        }
        // AgentRun 行锁保证同一个 run 的 SSE stream_seq 严格递增。
        long streamSeq = store.lockNextSseSequence(runId);
        String sseId = "sse_" + ids.nextId();
        store.insertSse(
                ids.nextId(),
                runId,
                sseId,
                streamSeq,
                event.runId() + ":" + event.eventId(),
                event.eventType(),
                payload);
        store.updateSseSequence(runId, streamSeq);
        if (isTerminal(status) && admission != null) {
            // 终态是释放 Redis lease 的唯一业务触发点；提升结果再回写数据库，Relay 才会继续发送。
            try {
                for (String promotedRunId : admission.releaseAndPromote(event.runId())) {
                    store.promoteOutbox(promotedRunId);
                }
            } catch (com.foodmate.shared.runtime.RuntimeException coordinationFailure) {
                // Redis 故障不能回滚已经接受的终态；lease 到期后由下一轮协调修复。
                if (!"RUNTIME_COORDINATION_UNAVAILABLE".equals(coordinationFailure.code()))
                    throw coordinationFailure;
            }
        }
        return new EventResult(event.runId(), event.eventId(), false, status);
    }

    public synchronized List<V1RunEvent> events(String runId) {
        if (store == null) return List.copyOf(memoryEvents.getOrDefault(runId, List.of()));
        return store.events(parseRunId(runId)).stream()
                .map(
                        row ->
                                new V1RunEvent(
                                        "v1",
                                        runId,
                                        row.dispatchId(),
                                        row.attempt(),
                                        row.eventId(),
                                        row.seq(),
                                        "persisted",
                                        "persisted",
                                        row.hash(),
                                        row.occurredAt(),
                                        row.type(),
                                        readPayload(row.payload())))
                .toList();
    }

    public synchronized List<SseRecord> sseEvents(String runId, long afterSequence) {
        if (store == null)
            return memoryEvents.getOrDefault(runId, List.of()).stream()
                    .filter(event -> event.eventSeq() > afterSequence)
                    .map(
                            event ->
                                    new SseRecord(
                                            event.eventSeq(),
                                            "sse_memory_" + event.eventId(),
                                            event.eventType(),
                                            event.payload(),
                                            event.eventType().equals("run.completed")
                                                    || event.eventType().equals("run.failed")
                                                    || event.eventType().equals("run.cancelled")))
                    .toList();
        return store.sseEvents(parseRunId(runId), afterSequence).stream()
                .map(
                        row ->
                                new SseRecord(
                                        row.seq(),
                                        row.id(),
                                        row.type(),
                                        readPayload(row.payload()),
                                        terminalType(row.type())))
                .toList();
    }

    public synchronized long cursorFor(String runId, String cursor) {
        if (cursor == null || cursor.isBlank()) return 0;
        try {
            return Math.max(0, Long.parseLong(cursor));
        } catch (NumberFormatException ignored) {
        }
        if (store == null) return 0;
        Long row = store.cursor(parseRunId(runId), cursor);
        return row == null ? 0 : row;
    }

    public synchronized String status(String runId) {
        // agent_runs.status 是唯一权威；superseded 等 Java 侧终态不产生 Python 事件，只能从投影读取。
        if (store != null) {
            String stored = store.status(parseRunId(runId));
            if (stored != null) return stored;
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
        if (store == null) return;
        long numeric = parseRunId(runId);
        if (!store.owned(numeric, userId)) {
            throw new com.foodmate.shared.error.BusinessException(
                    com.foodmate.shared.error.ErrorCode.FORBIDDEN);
        }
    }

    private EventResult acceptMemory(V1RunEvent event) {
        List<V1RunEvent> current =
                memoryEvents.computeIfAbsent(event.runId(), ignored -> new ArrayList<>());
        if (current.stream().anyMatch(item -> item.eventId().equals(event.eventId())))
            return new EventResult(event.runId(), event.eventId(), true, statusFor(event));
        long expected = memorySequence.getOrDefault(event.runId(), 0L) + 1;
        if (event.eventSeq() != expected)
            throw new com.foodmate.shared.runtime.RuntimeException(
                    event.eventSeq() < expected
                            ? "RUNTIME_EVENT_OUT_OF_ORDER"
                            : "RUNTIME_EVENT_GAP",
                    "event sequence is not contiguous");
        current.add(event);
        memorySequence.put(event.runId(), event.eventSeq());
        return new EventResult(event.runId(), event.eventId(), false, statusFor(event));
    }

    private void reject(V1RunEvent event, long runId, String reason, String code) {
        store.reject("rej_" + ids.nextId(), runId, event, reason, code, json(event));
    }

    private long parseRunId(String runId) {
        try {
            return Long.parseLong(runId);
        } catch (NumberFormatException e) {
            throw new com.foodmate.shared.runtime.RuntimeException(
                    "RUNTIME_STATE_CONFLICT", "run id is invalid");
        }
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new com.foodmate.shared.runtime.RuntimeException(
                    "RUNTIME_CONTRACT_INVALID", "payload is not JSON");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readPayload(String value) {
        try {
            return mapper.readValue(value, Map.class);
        } catch (JsonProcessingException e) {
            return Map.of();
        }
    }

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
            default ->
                    throw new com.foodmate.shared.runtime.RuntimeException(
                            "RUNTIME_CONTRACT_INVALID", "unsupported event type");
        };
    }

    public record EventResult(String runId, String eventId, boolean duplicate, String status) {}

    public record SseRecord(
            long streamSeq,
            String sseEventId,
            String eventType,
            Map<String, Object> payload,
            boolean terminal) {}

    private static boolean terminalType(String type) {
        return type.equals("run.completed")
                || type.equals("run.failed")
                || type.equals("run.cancelled")
                || type.equals("run.superseded");
    }

    private static boolean isTerminal(String status) {
        return status.equals("completed") || status.equals("failed") || status.equals("cancelled");
    }

    private void persistModelUsage(long runId, V1RunEvent event) {
        Map<String, Object> payload = event.payload();
        Map<String, Object> usage =
                payload.get("usage") instanceof Map<?, ?> value
                        ? (Map<String, Object>) value
                        : new HashMap<>();
        // usage_json 同时保留供应商 attempt 和价格版本，便于事后按原始计价配置审计成本。
        usage = new HashMap<>(usage);
        usage.put("model_call_id", payload.get("model_call_id"));
        usage.put("provider_attempt_id", payload.get("provider_attempt_id"));
        usage.put("provider_request_id", payload.get("provider_request_id"));
        usage.put("price_version", payload.get("price_version"));
        Map<String, Object> cost =
                payload.get("cost") instanceof Map<?, ?> value
                        ? (Map<String, Object>) value
                        : Map.of();
        Object amount = cost.get("amount");
        store.insertUsage(
                ids.nextId(),
                event,
                string(payload.get("scene")),
                string(payload.get("provider_code")),
                string(payload.get("model_name")),
                json(usage),
                number(payload.get("latency_ms")),
                amount == null ? null : new java.math.BigDecimal(amount.toString()),
                string(payload.getOrDefault("status", "success")));
    }

    private void persistAssistantMessage(long runId, Map<String, Object> result) {
        Object answer = result.get("answer");
        if (!(answer instanceof String text) || text.isBlank()) return;
        if (store.assistantExists(runId)) return;
        RunOwner owner = store.lockOwner(runId);
        if (owner == null) return;
        int sequence = store.nextMessageSequence(owner.sessionId());
        store.insertAssistant(ids.nextId(), runId, owner, text, json(result), sequence);
        store.touchSession(owner.sessionId());
    }

    private static String string(Object value) {
        return value == null ? null : value.toString();
    }

    private static Integer number(Object value) {
        try {
            return value == null ? null : Integer.valueOf(value.toString());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
