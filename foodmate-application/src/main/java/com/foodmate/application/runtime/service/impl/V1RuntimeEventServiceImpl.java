package com.foodmate.application.runtime.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.foodmate.application.common.service.AgentOperationMetrics;
import com.foodmate.application.common.service.OperationAuditService;
import com.foodmate.application.conversation.service.MemoryCandidateService;
import com.foodmate.application.runtime.admission.AgentAdmissionService;
import com.foodmate.application.runtime.port.out.RuntimeEventRepository;
import com.foodmate.application.runtime.port.out.RuntimeEventRepository.DispatchRow;
import com.foodmate.application.runtime.port.out.RuntimeEventRepository.RunOwner;
import com.foodmate.application.runtime.service.V1RuntimeEventService;
import com.foodmate.shared.id.IdGenerator;
import com.foodmate.shared.runtime.V1RunEvent;
import com.foodmate.shared.runtime.enums.DispatchState;
import com.foodmate.shared.runtime.enums.RunStatus;
import com.foodmate.shared.trace.TraceContext;
import com.foodmate.shared.trace.TraceContextHolder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 校验并投影 V1 Runtime 事件；网络发送由独立 publisher 负责。 */
@Service
public class V1RuntimeEventServiceImpl implements V1RuntimeEventService {
    private final RuntimeEventRepository store;
    private final IdGenerator ids;
    private final AgentAdmissionService admission;
    private final MemoryCandidateService memories;
    private final AgentOperationMetrics metrics;
    private final OperationAuditService audit;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final Map<String, List<V1RunEvent>> memoryEvents = new HashMap<>();
    private final Map<String, Long> memorySequence = new HashMap<>();

    public V1RuntimeEventServiceImpl(
            ObjectProvider<RuntimeEventRepository> storeProvider, IdGenerator ids) {
        this(storeProvider, ids, null, null);
    }

    public V1RuntimeEventServiceImpl(
            ObjectProvider<RuntimeEventRepository> storeProvider,
            IdGenerator ids,
            ObjectProvider<AgentAdmissionService> admissionProvider) {
        this(storeProvider, ids, admissionProvider, null);
    }

    public V1RuntimeEventServiceImpl(
            ObjectProvider<RuntimeEventRepository> storeProvider,
            IdGenerator ids,
            ObjectProvider<AgentAdmissionService> admissionProvider,
            ObjectProvider<MemoryCandidateService> memoryProvider) {
        this(storeProvider, ids, admissionProvider, memoryProvider, null, null);
    }

    public V1RuntimeEventServiceImpl(
            ObjectProvider<RuntimeEventRepository> storeProvider,
            IdGenerator ids,
            ObjectProvider<AgentAdmissionService> admissionProvider,
            ObjectProvider<MemoryCandidateService> memoryProvider,
            ObjectProvider<AgentOperationMetrics> metricsProvider) {
        this(storeProvider, ids, admissionProvider, memoryProvider, metricsProvider, null);
    }

    @Autowired
    public V1RuntimeEventServiceImpl(
            ObjectProvider<RuntimeEventRepository> storeProvider,
            IdGenerator ids,
            ObjectProvider<AgentAdmissionService> admissionProvider,
            ObjectProvider<MemoryCandidateService> memoryProvider,
            ObjectProvider<AgentOperationMetrics> metricsProvider,
            ObjectProvider<OperationAuditService> auditProvider) {
        this.store = storeProvider.getIfAvailable();
        this.ids = ids;
        this.admission = admissionProvider == null ? null : admissionProvider.getIfAvailable();
        this.memories = memoryProvider == null ? null : memoryProvider.getIfAvailable();
        this.metrics = metricsProvider == null ? null : metricsProvider.getIfAvailable();
        this.audit = auditProvider == null ? null : auditProvider.getIfAvailable();
    }

    @Transactional
    @Override
    public synchronized EventResult accept(V1RunEvent event) {
        // 先做幂等、dispatch fencing 和连续 event_seq 校验，合法事件才允许推进状态。
        if (store == null) {
            EventResult result = acceptMemory(event);
            if (metrics != null)
                metrics.count(
                        "local", "event", result.duplicate() ? "duplicate" : "success", "memory");
            return result;
        }
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
            EventResult result =
                    new EventResult(event.runId(), event.eventId(), true, statusFor(event));
            if (metrics != null) metrics.count("rocketmq", "event", "duplicate", "inbox");
            return result;
        }
        DispatchRow dispatch = store.dispatch(runId, event.dispatchId());
        if (dispatch == null
                || !DispatchState.ACTIVE.code().equals(dispatch.state())
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
        JsonNode projectedPayload = visibleCitations(event.payload());
        auditContextAssembly(event, runId, projectedPayload);
        String payload = json(projectedPayload);
        store.insertEvent(ids.nextId(), runId, event, payload);
        if ("run.model_usage".equals(event.eventType())) persistModelUsage(runId, event);
        store.updateDispatch(runId, event.dispatchId(), event.eventSeq(), status);
        String result = json(projectedPayload);
        if (changesRunStatus) {
            // 终态后不允许任何状态回退；superseded 等 Java 侧终态同样受保护。
            store.updateRun(runId, status, result);
            if (RunStatus.COMPLETED.code().equals(status)) {
                JsonNode resultPayload = readPayload(result);
                String resultType = resultPayload.path("result_type").asText(null);
                if ("normal".equals(resultType) || "safety_degraded".equals(resultType)) {
                    store.setResultType(runId, resultType);
                }
                persistAssistantMessage(runId, resultPayload);
                if (memories != null)
                    memories.persistFromCompletedRun(runId, completedRunPayload(resultPayload));
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
        EventResult eventResult = new EventResult(event.runId(), event.eventId(), false, status);
        if (metrics != null) metrics.count("rocketmq", "event", "success", status);
        return eventResult;
    }

    @Override
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

    /** Returns whether the run belongs to the V1 durable runtime or the in-memory test runtime. */
    @Override
    public synchronized boolean exists(String runId) {
        if (store == null) return memoryEvents.containsKey(runId);
        return store.runExists(parseRunId(runId));
    }

    @Override
    public synchronized List<SseRecord> sseEvents(String runId, long afterSequence) {
        if (store == null) {
            List<SseRecord> replay =
                    memoryEvents.getOrDefault(runId, List.of()).stream()
                            .filter(event -> event.eventSeq() > afterSequence)
                            .map(
                                    event ->
                                            new SseRecord(
                                                    event.eventSeq(),
                                                    "sse_memory_" + event.eventId(),
                                                    event.eventType(),
                                                    event.payload(),
                                                    event.eventType().equals("run.completed")
                                                            || event.eventType()
                                                                    .equals("run.failed")
                                                            || event.eventType()
                                                                    .equals("run.cancelled")))
                            .toList();
            if (metrics != null) metrics.count("local", "sse_replay", "success", "last_event_id");
            return replay;
        }
        List<SseRecord> replay =
                store.sseEvents(parseRunId(runId), afterSequence).stream()
                        .map(
                                row ->
                                        new SseRecord(
                                                row.seq(),
                                                row.id(),
                                                row.type(),
                                                readPayload(row.payload()),
                                                terminalType(row.type())))
                        .toList();
        if (metrics != null) metrics.count("rocketmq", "sse_replay", "success", "last_event_id");
        return replay;
    }

    @Override
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

    @Override
    public synchronized String status(String runId) {
        // agent_runs.status 是唯一权威；superseded 等 Java 侧终态不产生 Python 事件，只能从投影读取。
        if (store != null) {
            String stored = store.status(parseRunId(runId));
            if (stored != null) return stored;
        }
        List<V1RunEvent> history = events(runId);
        if (history.isEmpty()) return RunStatus.QUEUED.code();
        String status = RunStatus.QUEUED.code();
        for (V1RunEvent event : history) {
            String candidate = statusFor(event);
            if (!"unchanged".equals(candidate)) status = candidate;
        }
        return status;
    }

    @Override
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

    private JsonNode readPayload(String value) {
        try {
            return mapper.readTree(value);
        } catch (JsonProcessingException e) {
            return mapper.createObjectNode();
        }
    }

    private String statusFor(V1RunEvent event) {
        return switch (event.eventType()) {
            case "run.accepted" -> RunStatus.QUEUED.code();
            case "run.routed" -> RunStatus.ROUTED.code();
            case "run.clarification_requested" -> RunStatus.WAITING_USER.code();
            case "run.planned" -> RunStatus.PLANNING.code();
            case "run.retrieval_started", "run.retrieval_finished" -> RunStatus.RETRIEVING.code();
            case "run.tool_started", "run.tool_finished" -> RunStatus.EXECUTING.code();
            case "run.answer_stream" -> RunStatus.VALIDATING.code();
            case "run.completed" -> RunStatus.COMPLETED.code();
            case "run.failed" -> RunStatus.FAILED.code();
            case "run.cancelled" -> RunStatus.CANCELLED.code();
            case "run.cancel_acknowledged",
                    "run.model_usage",
                    "run.checkpoint_saved",
                    "run.eval_decided",
                    "run.context_assembled" ->
                    "unchanged";
            default ->
                    throw new com.foodmate.shared.runtime.RuntimeException(
                            "RUNTIME_CONTRACT_INVALID", "unsupported event type");
        };
    }

    private static boolean terminalType(String type) {
        return type.equals("run.completed")
                || type.equals("run.failed")
                || type.equals("run.cancelled")
                || type.equals("run.superseded");
    }

    private static boolean isTerminal(String status) {
        if ("unchanged".equals(status)) return false;
        return RunStatus.fromCode(status).isTerminal();
    }

    private void auditContextAssembly(V1RunEvent event, long runId, JsonNode payload) {
        if (audit == null || !"run.context_assembled".equals(event.eventType())) return;
        RunOwner owner = store.lockOwner(runId);
        if (owner == null)
            throw new com.foodmate.shared.runtime.RuntimeException(
                    "RUNTIME_STATE_CONFLICT", "run owner is missing");
        TraceContextHolder.runWith(
                TraceContext.of(event.requestId(), event.traceId()),
                () ->
                        audit.record(
                                owner.userId(),
                                "agent_run",
                                Long.toString(runId),
                                "agent_run.context.assembled",
                                "success",
                                null,
                                null,
                                null,
                                Map.of(
                                        "context_source_message_ids",
                                                sourceIds(payload, "message_id"),
                                        "context_source_summary_ids",
                                                sourceIds(payload, "summary_id"),
                                        "context_source_memory_ids",
                                                sourceIds(payload, "memory_id"),
                                        "context_source_citation_ids",
                                                sourceIds(payload, "citation_id"))));
    }

    private String sourceIds(JsonNode payload, String sourceType) {
        JsonNode values = payload.path("source_ids").path(sourceType);
        if (!values.isArray()) return "";
        StringJoiner result = new StringJoiner(",");
        for (JsonNode value : values) {
            if (!value.isTextual() || value.asText().isBlank()) continue;
            String id = value.asText();
            result.add(id.substring(0, Math.min(id.length(), 128)));
        }
        return result.toString();
    }

    private void persistModelUsage(long runId, V1RunEvent event) {
        JsonNode payload = event.payload();
        ObjectNode usage =
                payload.path("usage").isObject()
                        ? payload.path("usage").deepCopy()
                        : mapper.createObjectNode();
        // usage_json 同时保留供应商 attempt 和价格版本，便于事后按原始计价配置审计成本。
        copyField(usage, payload, "model_call_id");
        copyField(usage, payload, "provider_attempt_id");
        copyField(usage, payload, "provider_request_id");
        copyField(usage, payload, "price_version");
        copyField(usage, payload, "route_version");
        copyField(usage, payload, "budget_policy_version");
        JsonNode amount = payload.path("cost").path("amount");
        store.insertUsage(
                ids.nextId(),
                event,
                string(payload.get("scene")),
                string(payload.get("provider_code")),
                string(payload.get("model_name")),
                json(usage),
                number(payload.get("latency_ms")),
                amount.isMissingNode() || amount.isNull()
                        ? null
                        : new java.math.BigDecimal(amount.asText()),
                payload.path("status").asText("success"),
                string(payload.get("route_version")),
                string(payload.get("price_version")),
                string(payload.get("budget_policy_version")));
    }

    private void persistAssistantMessage(long runId, JsonNode result) {
        String text = string(result.get("answer"));
        if (text == null || text.isBlank()) return;
        if (store.assistantExists(runId)) return;
        RunOwner owner = store.lockOwner(runId);
        if (owner == null) return;
        int sequence = store.nextMessageSequence(owner.sessionId());
        store.insertAssistant(ids.nextId(), runId, owner, text, json(result), sequence);
        store.touchSession(owner.sessionId());
    }

    private static String string(JsonNode value) {
        return value == null || value.isNull() || value.isMissingNode() ? null : value.asText();
    }

    private JsonNode visibleCitations(JsonNode payload) {
        if (store == null || !payload.isObject() || !payload.has("citations")) return payload;
        ObjectNode copy = ((ObjectNode) payload).deepCopy();
        var allowed = mapper.createArrayNode();
        JsonNode citations = payload.path("citations");
        if (citations.isArray()) {
            for (JsonNode citation : citations) {
                try {
                    long documentId = Long.parseLong(citation.path("document_id").asText());
                    String version = citation.path("version").asText();
                    if (!version.isBlank() && store.publicCitationVisible(documentId, version))
                        allowed.add(citation);
                } catch (NumberFormatException ignored) {
                    // Invalid citations are not allowed into persisted SSE projections.
                }
            }
        }
        copy.set("citations", allowed);
        return copy;
    }

    private static Integer number(JsonNode value) {
        try {
            return value == null || value.isNull() || value.isMissingNode()
                    ? null
                    : Integer.valueOf(value.asText());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static void copyField(ObjectNode target, JsonNode source, String field) {
        JsonNode value = source.get(field);
        target.set(
                field,
                value == null ? com.fasterxml.jackson.databind.node.NullNode.instance : value);
    }

    private MemoryCandidateService.CompletedRunPayload completedRunPayload(JsonNode payload) {
        JsonNode rawCandidates = payload.get("memory_candidates");
        if (rawCandidates == null || !rawCandidates.isArray())
            return new MemoryCandidateService.CompletedRunPayload(List.of());
        List<MemoryCandidateService.MemoryCandidate> candidates = new ArrayList<>();
        for (JsonNode candidate : rawCandidates) {
            if (!candidate.isObject()) continue;
            List<String> sourceMessageIds = new ArrayList<>();
            JsonNode rawIds = candidate.get("source_message_ids");
            if (rawIds != null && rawIds.isArray()) {
                for (JsonNode id : rawIds) {
                    if (id.isTextual() && !id.asText().isBlank()) sourceMessageIds.add(id.asText());
                }
            }
            candidates.add(
                    new MemoryCandidateService.MemoryCandidate(
                            string(candidate.get("memory_type")),
                            string(candidate.get("memory_key")),
                            candidate.get("memory_value"),
                            decimal(candidate.get("confidence")),
                            string(candidate.get("source")),
                            string(candidate.get("scope")),
                            sourceMessageIds));
        }
        return new MemoryCandidateService.CompletedRunPayload(candidates);
    }

    private static java.math.BigDecimal decimal(JsonNode value) {
        try {
            return value == null || value.isNull() || value.isMissingNode()
                    ? null
                    : new java.math.BigDecimal(value.asText());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
