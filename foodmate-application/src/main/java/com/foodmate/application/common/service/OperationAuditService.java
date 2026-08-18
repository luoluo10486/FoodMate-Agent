package com.foodmate.application.common.service;

import com.foodmate.application.common.port.out.OperationAuditPort;
import com.foodmate.application.common.port.out.OperationAuditPort.AuditRecord;
import com.foodmate.shared.id.IdGenerator;
import com.foodmate.shared.trace.TraceContext;
import com.foodmate.shared.trace.TraceContextHolder;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** 业务层统一构造审计事实；原始业务内容不会进入审计 JSON。 */
@Service
public class OperationAuditService {
    private final OperationAuditPort store;
    private final IdGenerator ids;
    private final TransactionTemplate failureTransaction;

    /** Compatibility constructor for unit tests and application-only callers. */
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
        if (store == null) return;
        TraceContext trace = TraceContextHolder.currentOrNew();
        String safeMetadata = safeJson(metadata);
        int inserted =
                store.insert(
                        new AuditRecord(
                                ids.nextId(),
                                operatorId,
                                trace.requestId(),
                                trace.traceId(),
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

    /** 完成带幂等键的业务审计。 */
    public void complete(long operatorId, String idempotencyKey, String responseJson) {
        if (store == null) return;
        if (store.complete(operatorId, idempotencyKey, safeJson(responseJson)) != 1)
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
        if (store.transition(operatorId, idempotencyKey, result, errorCode, safeJson(responseJson))
                != 1)
            throw new IllegalStateException("operation audit transition was not persisted");
    }

    /** Records a rejection or failure after the caller's business transaction has rolled back. */
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
        if (store == null) return;
        if (failureTransaction == null) {
            record(
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

    private static String safeJson(String value) {
        return value == null || value.isBlank() ? "{}" : value;
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
        return normalized.contains("password")
                || normalized.contains("token")
                || normalized.contains("secret")
                || normalized.contains("prompt")
                || normalized.contains("answer")
                || normalized.contains("content")
                || normalized.contains("note")
                || normalized.contains("request_body")
                || normalized.contains("response_body");
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
