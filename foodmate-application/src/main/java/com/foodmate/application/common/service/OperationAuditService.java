package com.foodmate.application.common.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.foodmate.application.common.port.out.OperationAuditPort;
import com.foodmate.application.common.port.out.OperationAuditPort.AuditRecord;
import com.foodmate.shared.id.IdGenerator;
import com.foodmate.shared.trace.TraceContext;
import com.foodmate.shared.trace.TraceContextHolder;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** 业务层统一构造审计事实；原始业务内容不会进入审计 JSON。 */
@Service
public class OperationAuditService {
    private static final int MAX_SUMMARY_DEPTH = 4;
    private static final int MAX_SUMMARY_ARRAY_ITEMS = 16;
    private static final int MAX_SUMMARY_STRING_LENGTH = 256;
    private static final Set<String> SENSITIVE_KEYS =
            Set.of(
                    "password",
                    "token",
                    "secret",
                    "api_key",
                    "prompt",
                    "answer",
                    "content",
                    "note",
                    "notes",
                    "comment",
                    "items",
                    "days_plan",
                    "shopping_list",
                    "raw_payload",
                    "payload",
                    "original_request",
                    "request_body",
                    "response_body",
                    "object_key",
                    "presigned_url");
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    private final OperationAuditPort store;
    private final IdGenerator ids;
    private final TransactionTemplate failureTransaction;

    /** 供单元测试和仅依赖 application 模块的调用方使用的兼容构造函数。 */
    public OperationAuditService(
            ObjectProvider<OperationAuditPort> storeProvider, IdGenerator ids) {
        this(storeProvider, ids, null);
    }

    @Autowired
    public OperationAuditService(
            ObjectProvider<OperationAuditPort> storeProvider,
            IdGenerator ids,
            ObjectProvider<PlatformTransactionManager> transactionManagerProvider) {
        this.store = storeProvider.getIfAvailable();
        this.ids = Objects.requireNonNull(ids, "IdGenerator is required");
        PlatformTransactionManager transactionManager =
                transactionManagerProvider == null
                        ? null
                        : transactionManagerProvider.getIfAvailable();
        if (transactionManager == null) {
            this.failureTransaction = null;
        } else {
            this.failureTransaction = new TransactionTemplate(transactionManager);
            this.failureTransaction.setPropagationBehavior(
                    TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        }
    }

    /** 写入一条业务命令终态审计。 */
    public void record(
            Long operatorId,
            String targetType,
            String targetId,
            String action,
            String result,
            String errorCode,
            String parametersDigest,
            String idempotencyKey,
            Map<String, ?> metadata) {
        record(
                TraceContextHolder.currentOrNew(),
                operatorId,
                targetType,
                targetId,
                action,
                result,
                errorCode,
                parametersDigest,
                idempotencyKey,
                metadata);
    }

    /** 使用拥有业务命令的 Trace 上下文写入审计事实。 */
    public void record(
            TraceContext trace,
            Long operatorId,
            String targetType,
            String targetId,
            String action,
            String result,
            String errorCode,
            String parametersDigest,
            String idempotencyKey,
            Map<String, ?> metadata) {
        if (store == null) return;
        TraceContext effectiveTrace = trace == null ? TraceContextHolder.currentOrNew() : trace;
        String safeMetadata = safeJson(metadata);
        int inserted =
                store.insert(
                        new AuditRecord(
                                ids.nextId(),
                                operatorId,
                                effectiveTrace.requestId(),
                                effectiveTrace.traceId(),
                                required(targetType, "targetType"),
                                targetId,
                                required(action, "action"),
                                required(result, "result"),
                                errorCode,
                                safeMetadata,
                                "{\"result\":\"" + escape(result) + "\"}",
                                parametersDigest,
                                idempotencyKey));
        if (inserted != 1) throw new IllegalStateException("operation audit was not persisted");
    }

    /** 以 pending 状态占用幂等键；重复请求必须由调用方读取并重放既有事实。 */
    public int reserve(AuditRecord record) {
        if (store == null) return 1;
        return store.reserve(record);
    }

    /** 构造并占用一条业务写操作的 pending 审计事实。 */
    public int reserve(
            Long operatorId,
            String targetType,
            String targetId,
            String action,
            String parametersDigest,
            String idempotencyKey,
            Map<String, ?> metadata) {
        if (store == null) return 1;
        TraceContext trace = TraceContextHolder.currentOrNew();
        return reserve(
                new AuditRecord(
                        ids.nextId(),
                        operatorId,
                        trace.requestId(),
                        trace.traceId(),
                        required(targetType, "targetType"),
                        targetId,
                        required(action, "action"),
                        "pending",
                        null,
                        safeJson(metadata),
                        "{}",
                        parametersDigest,
                        idempotencyKey));
    }

    /** 查询操作者和幂等键对应的审计事实。 */
    public OperationAuditPort.IdempotencyRecord findIdempotency(
            long operatorId, String idempotencyKey) {
        return store == null ? null : store.findIdempotency(operatorId, idempotencyKey);
    }

    /** 完成带幂等键的业务审计。 */
    public void complete(long operatorId, String idempotencyKey, String responseJson) {
        if (store == null) return;
        if (store.complete(operatorId, idempotencyKey, safeResponseJson(responseJson)) != 1)
            throw new IllegalStateException("operation audit completion was not persisted");
    }

    /** 记录失败或其他终态；用于业务事务回滚后的独立失败审计。 */
    public void transition(
            long operatorId,
            String idempotencyKey,
            String result,
            String errorCode,
            String responseJson) {
        if (store == null) return;
        if (store.transition(
                        operatorId,
                        idempotencyKey,
                        result,
                        errorCode,
                        safeResponseJson(responseJson))
                != 1)
            throw new IllegalStateException("operation audit transition was not persisted");
    }

    /** 在调用方业务事务回滚后记录拒绝或失败事实。 */
    public void recordFailure(
            Long operatorId,
            String targetType,
            String targetId,
            String action,
            String result,
            String errorCode,
            String parametersDigest,
            String idempotencyKey,
            Map<String, ?> metadata) {
        recordFailure(
                TraceContextHolder.currentOrNew(),
                operatorId,
                targetType,
                targetId,
                action,
                result,
                errorCode,
                parametersDigest,
                idempotencyKey,
                metadata);
    }

    /** 使用拥有失败命令的 Trace 上下文记录失败事实。 */
    public void recordFailure(
            TraceContext trace,
            Long operatorId,
            String targetType,
            String targetId,
            String action,
            String result,
            String errorCode,
            String parametersDigest,
            String idempotencyKey,
            Map<String, ?> metadata) {
        if (store == null) return;
        if (failureTransaction == null) {
            record(
                    trace,
                    operatorId,
                    targetType,
                    targetId,
                    action,
                    result,
                    errorCode,
                    parametersDigest,
                    idempotencyKey,
                    metadata);
            return;
        }
        failureTransaction.executeWithoutResult(
                ignored ->
                        record(
                                trace,
                                operatorId,
                                targetType,
                                targetId,
                                action,
                                result,
                                errorCode,
                                parametersDigest,
                                idempotencyKey,
                                metadata));
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException(name + " is required");
        return value;
    }

    private static String safeResponseJson(String value) {
        if (value == null || value.isBlank()) return "{}";
        try {
            return JSON.writeValueAsString(sanitize(JSON.readTree(value), 0));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "operation audit response summary is invalid", exception);
        }
    }

    private static String safeJson(Map<String, ?> metadata) {
        if (metadata == null || metadata.isEmpty()) return "{}";
        StringBuilder json = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, ?> entry : metadata.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank() || sensitive(entry.getKey()))
                continue;
            if (!first) json.append(',');
            first = false;
            json.append('"').append(escape(entry.getKey())).append("\":\"");
            String value = String.valueOf(entry.getValue());
            json.append(escape(value.substring(0, Math.min(value.length(), 256)))).append('"');
        }
        return json.append('}').toString();
    }

    private static boolean sensitive(String key) {
        String normalized = key.toLowerCase(java.util.Locale.ROOT);
        return SENSITIVE_KEYS.contains(normalized)
                || normalized.contains("password")
                || normalized.contains("token")
                || normalized.contains("secret")
                || normalized.contains("prompt")
                || normalized.contains("answer")
                || normalized.contains("content")
                || normalized.contains("note")
                || normalized.endsWith("_token")
                || normalized.endsWith("_secret")
                || normalized.endsWith("_password")
                || normalized.endsWith("_api_key");
    }

    private static JsonNode sanitize(JsonNode value, int depth) {
        if (value == null || value.isNull()) return JSON.nullNode();
        if (depth >= MAX_SUMMARY_DEPTH) return TextNode.valueOf("[truncated]");
        if (value.isObject()) {
            ObjectNode result = JSON.createObjectNode();
            value.fields()
                    .forEachRemaining(
                            entry -> {
                                if (!sensitive(entry.getKey()))
                                    result.set(
                                            entry.getKey(), sanitize(entry.getValue(), depth + 1));
                            });
            return result;
        }
        if (value.isArray()) {
            ArrayNode result = JSON.createArrayNode();
            int count = 0;
            for (JsonNode item : value) {
                if (count++ >= MAX_SUMMARY_ARRAY_ITEMS) break;
                result.add(sanitize(item, depth + 1));
            }
            return result;
        }
        if (value.isTextual())
            return TextNode.valueOf(
                    value.textValue()
                            .substring(
                                    0,
                                    Math.min(
                                            value.textValue().length(),
                                            MAX_SUMMARY_STRING_LENGTH)));
        return value;
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
