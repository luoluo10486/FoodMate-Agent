package com.foodmate.application.account.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.foodmate.application.account.port.out.AdminExportRepository;
import com.foodmate.application.account.port.out.AdminExportRepository.JobRow;
import com.foodmate.application.common.port.out.ObjectStoragePort;
import com.foodmate.application.common.service.OperationAuditService;
import com.foodmate.shared.account.enums.UserRole;
import com.foodmate.shared.error.BusinessException;
import com.foodmate.shared.error.ErrorCode;
import com.foodmate.shared.id.IdGenerator;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Creates and processes small, redacted administrator export jobs. */
@Service
public class AdminExportService {
    private static final int MAX_ROWS = 100;
    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 128;
    private static final String FAILED_CODE = "ADMIN_EXPORT_FAILED";
    private static final String BUCKET_DEFAULT = "foodmate-private";

    private static final Map<String, Set<String>> RESOURCE_FIELDS =
            Map.of(
                    "runs",
                    Set.of(
                            "agent_run_id",
                            "session_id",
                            "intent",
                            "status",
                            "trace_id",
                            "duration_ms",
                            "actor_ref"),
                    "users",
                    Set.of("user_id", "username", "role", "status", "email_ref"),
                    "tool-calls",
                    Set.of(
                            "tool_call_id",
                            "agent_run_id",
                            "tool_name",
                            "status",
                            "latency_ms",
                            "trace_id"),
                    "sql-audits",
                    Set.of(
                            "sql_audit_id",
                            "actor",
                            "query_hash",
                            "result",
                            "trace_id",
                            "latency_ms",
                            "row_count",
                            "error_code",
                            "created_at"),
                    "tools",
                    Set.of("name", "version", "risk", "status", "scope", "owner", "last_called_at"),
                    "usage",
                    Set.of("provider", "model", "scene", "tokens", "cost", "latency_ms", "status"),
                    "knowledge",
                    Set.of(
                            "document_id",
                            "title",
                            "status",
                            "visibility",
                            "chunks",
                            "source",
                            "index_progress",
                            "updated_at"),
                    "deleted",
                    Set.of("resource_type", "resource_id", "owner_ref", "deleted_at", "reason"),
                    "operation-audits",
                    Set.of(
                            "operator_id",
                            "action",
                            "target_type",
                            "target_id",
                            "result",
                            "request_id",
                            "trace_id",
                            "created_at"));

    private final AdminExportRepository store;
    private final AdminOperationalQueryService queries;
    private final ObjectStoragePort storage;
    private final IdGenerator ids;
    private final ObjectMapper mapper;
    private final OperationAuditService audit;
    private final String bucket;

    public AdminExportService(
            ObjectProvider<AdminExportRepository> store,
            AdminOperationalQueryService queries,
            ObjectProvider<ObjectStoragePort> storage,
            IdGenerator ids,
            ObjectMapper mapper,
            OperationAuditService audit,
            @org.springframework.beans.factory.annotation.Value(
                            "${foodmate.storage.bucket:foodmate-private}")
                    String bucket) {
        this.store = store.getIfAvailable();
        this.queries = Objects.requireNonNull(queries);
        this.storage = storage.getIfAvailable();
        this.ids = Objects.requireNonNull(ids);
        this.mapper = mapper.copy().findAndRegisterModules();
        this.audit = Objects.requireNonNull(audit);
        this.bucket = bucket == null || bucket.isBlank() ? BUCKET_DEFAULT : bucket;
    }

    @Transactional
    public Created request(long operatorId, UserRole role, Request request, String idempotencyKey) {
        String resource = normalizeResource(request == null ? null : request.resource());
        requireRole(role, resource);
        String key = requireIdempotencyKey(idempotencyKey);
        Request normalized = normalizeRequest(request, resource);
        String digest = digest(normalized, key);
        var previous = audit.findIdempotency(operatorId, key);
        if (previous != null)
            return replay(
                    previous.parametersDigest(),
                    previous.result(),
                    previous.responseJson(),
                    digest);

        boolean reserved = false;
        try {
            if (audit.reserve(
                            operatorId,
                            "admin_export_request",
                            digest.substring(0, 24),
                            "admin.export.request",
                            digest,
                            key,
                            Map.of("resource", resource, "row_limit", MAX_ROWS))
                    != 1) {
                previous = audit.findIdempotency(operatorId, key);
                if (previous != null)
                    return replay(
                            previous.parametersDigest(),
                            previous.result(),
                            previous.responseJson(),
                            digest);
                throw new BusinessException(ErrorCode.CONFLICT, "导出幂等请求无法占用");
            }
            reserved = true;
            if (store == null)
                throw new IllegalStateException("admin export persistence unavailable");
            long jobId = ids.nextId();
            if (store.insertJob(
                            jobId,
                            operatorId,
                            resource,
                            filtersJson(normalized),
                            fieldsJson(normalized.fields()))
                    != 1) throw new IllegalStateException("admin export job was not persisted");
            Created result = new Created(jobId);
            audit.complete(
                    operatorId,
                    key,
                    mapper.createObjectNode().put("export_job_id", jobId).toString());
            return result;
        } catch (RuntimeException exception) {
            if (reserved)
                registerFailureAfterRollback(operatorId, key, resource, digest, exception);
            else recordFailure(operatorId, resource, digest, key, exception);
            throw exception;
        }
    }

    public Status status(long operatorId, long jobId) {
        JobRow job = ownJob(operatorId, jobId);
        return new Status(
                job.jobId(),
                job.resource(),
                job.status(),
                job.expiresAt(),
                job.completedAt(),
                job.consumedAt(),
                job.failureCode());
    }

    @Transactional
    public String consume(long operatorId, long jobId) {
        if (store == null || storage == null)
            throw new IllegalStateException("admin export unavailable");
        ownJob(operatorId, jobId);
        if (store.consumeJob(operatorId, jobId) != 1)
            throw new BusinessException(ErrorCode.CONFLICT, "导出不可下载、已过期或已消费");
        try {
            String url =
                    storage.presignedGet(bucket, store.objectKey(jobId), Duration.ofMinutes(10));
            audit.record(
                    operatorId,
                    "admin_export_job",
                    Long.toString(jobId),
                    "admin.export.consume",
                    "success",
                    null,
                    null,
                    null,
                    Map.of());
            return url;
        } catch (RuntimeException exception) {
            audit.recordFailure(
                    operatorId,
                    "admin_export_job",
                    Long.toString(jobId),
                    "admin.export.consume",
                    "failed",
                    FAILED_CODE,
                    null,
                    null,
                    Map.of("failure", "presigned_url"));
            throw new IllegalStateException("admin export download unavailable", exception);
        }
    }

    @Scheduled(fixedDelayString = "${foodmate.admin.export-poll-ms:30000}")
    public synchronized void processJobs() {
        if (store == null || storage == null) return;
        for (Long jobId : store.queuedJobs(2)) processJob(jobId);
    }

    private void processJob(long jobId) {
        JobRow job = store.find(jobId);
        if (job == null || store.startJob(jobId) != 1) return;
        try {
            Request request = requestFromJob(job);
            var page = queries.query(job.resource(), request.toQueryRequest());
            byte[] content = exportJson(job, request, page.items());
            String key = "admin-exports/" + job.operatorId() + "/" + job.jobId() + ".json";
            storage.put(
                    bucket,
                    key,
                    new ByteArrayInputStream(content),
                    content.length,
                    "application/json");
            if (store.completeJob(job.jobId(), key) != 1)
                throw new IllegalStateException("admin export completion was not persisted");
        } catch (RuntimeException exception) {
            store.failJob(job.jobId(), FAILED_CODE);
            audit.recordFailure(
                    job.operatorId(),
                    "admin_export_job",
                    Long.toString(job.jobId()),
                    "admin.export.process",
                    "failed",
                    FAILED_CODE,
                    null,
                    null,
                    Map.of("failure", "worker"));
        }
    }

    private byte[] exportJson(JobRow job, Request request, List<?> rows) {
        ObjectNode root = mapper.createObjectNode();
        root.put("resource", job.resource());
        root.put("row_limit", MAX_ROWS);
        root.put("returned_rows", rows.size());
        root.put("generated_at", java.time.Instant.now().toString());
        root.set("fields", mapper.valueToTree(request.fields()));
        ArrayNode items = root.putArray("items");
        for (Object row : rows) {
            ObjectNode source = mapper.valueToTree(row);
            ObjectNode safe = mapper.createObjectNode();
            for (String field : request.fields())
                if (source.has(field)) safe.set(field, source.get(field));
            items.add(safe);
        }
        return root.toString().getBytes(StandardCharsets.UTF_8);
    }

    private Request requestFromJob(JobRow job) {
        try {
            var filters = mapper.readTree(job.filtersJson());
            var fields = mapper.readTree(job.fieldsJson());
            return new Request(
                    job.resource(),
                    text(filters, "query"),
                    text(filters, "status"),
                    text(filters, "visibility"),
                    text(filters, "sort"),
                    text(filters, "direction"),
                    mapper.convertValue(
                            fields,
                            mapper.getTypeFactory()
                                    .constructCollectionType(List.class, String.class)));
        } catch (Exception exception) {
            throw new IllegalStateException("admin export filters are invalid", exception);
        }
    }

    private JobRow ownJob(long operatorId, long jobId) {
        if (store == null) throw new IllegalStateException("admin export persistence unavailable");
        JobRow job = store.find(jobId);
        if (job == null || job.operatorId() != operatorId)
            throw new BusinessException(ErrorCode.NOT_FOUND, "导出任务不存在");
        return job;
    }

    private Request normalizeRequest(Request request, String resource) {
        if (request == null) throw invalid("导出参数不能为空");
        Set<String> allowed = RESOURCE_FIELDS.get(resource);
        List<String> fields =
                (request.fields() == null || request.fields().isEmpty()
                        ? allowed.stream().sorted().toList()
                        : request.fields().stream()
                                .map(
                                        value ->
                                                value == null
                                                        ? ""
                                                        : value.trim().toLowerCase(Locale.ROOT))
                                .distinct()
                                .toList());
        if (fields.isEmpty() || fields.stream().anyMatch(value -> !allowed.contains(value)))
            throw invalid("导出字段不在安全白名单中");
        return new Request(
                resource,
                normalizeText(request.query(), 128),
                normalizeText(request.status(), 32),
                normalizeText(request.visibility(), 32),
                normalizeText(request.sort(), 32),
                normalizeDirection(request.direction()),
                fields);
    }

    private String normalizeResource(String resource) {
        String value = resource == null ? "" : resource.trim().toLowerCase(Locale.ROOT);
        if (!RESOURCE_FIELDS.containsKey(value)) throw invalid("不支持的导出资源");
        return value;
    }

    private static void requireRole(UserRole role, String resource) {
        if (role != UserRole.ADMIN && role != UserRole.SUPERADMIN)
            throw new BusinessException(ErrorCode.FORBIDDEN, "当前角色不能导出管理数据");
        if (role == UserRole.ADMIN && ("users".equals(resource) || "deleted".equals(resource)))
            throw new BusinessException(ErrorCode.FORBIDDEN, "当前角色不能导出该资源");
    }

    private static String requireIdempotencyKey(String value) {
        if (value == null || value.isBlank() || value.length() > MAX_IDEMPOTENCY_KEY_LENGTH)
            throw invalid("Idempotency-Key 无效");
        return value.trim();
    }

    private static String normalizeText(String value, int maxLength) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > maxLength) throw invalid("导出筛选条件过长");
        return normalized;
    }

    private static String normalizeDirection(String value) {
        String normalized = normalizeText(value, 4);
        if (normalized == null) return "desc";
        if (!"asc".equalsIgnoreCase(normalized) && !"desc".equalsIgnoreCase(normalized))
            throw invalid("direction must be asc or desc");
        return normalized.toLowerCase(Locale.ROOT);
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.INVALID_ARGUMENT, message);
    }

    private String filtersJson(Request request) {
        try {
            return mapper.createObjectNode()
                    .put("query", request.query())
                    .put("status", request.status())
                    .put("visibility", request.visibility())
                    .put("sort", request.sort())
                    .put("direction", request.direction())
                    .toString();
        } catch (RuntimeException exception) {
            throw new IllegalStateException(
                    "admin export filters could not be serialized", exception);
        }
    }

    private String fieldsJson(List<String> fields) {
        return mapper.valueToTree(fields).toString();
    }

    private String digest(Request request, String key) {
        String canonical =
                String.join(
                        "|",
                        key,
                        request.resource(),
                        String.valueOf(request.query()),
                        String.valueOf(request.status()),
                        String.valueOf(request.visibility()),
                        String.valueOf(request.sort()),
                        request.direction(),
                        String.join(",", request.fields()));
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private Created replay(
            String previousDigest, String previousResult, String responseJson, String digest) {
        if (!digest.equals(previousDigest))
            throw new BusinessException(ErrorCode.CONFLICT, "幂等键对应的导出参数已变化");
        if (!"success".equalsIgnoreCase(previousResult))
            throw new BusinessException(ErrorCode.CONFLICT, "导出幂等请求正在处理或已失败");
        try {
            return new Created(mapper.readTree(responseJson).path("export_job_id").asLong(0));
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "admin export idempotency response is invalid", exception);
        }
    }

    private void recordFailure(
            long operatorId,
            String resource,
            String digest,
            String key,
            RuntimeException exception) {
        audit.recordFailure(
                operatorId,
                "admin_export_request",
                digest.substring(0, 24),
                "admin.export.request",
                "failed",
                FAILED_CODE,
                digest,
                key,
                Map.of("resource", resource));
    }

    private void registerFailureAfterRollback(
            long operatorId,
            String key,
            String resource,
            String digest,
            RuntimeException exception) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            recordFailure(operatorId, resource, digest, key, exception);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        recordFailure(operatorId, resource, digest, key, exception);
                    }
                });
    }

    private static String text(com.fasterxml.jackson.databind.JsonNode node, String name) {
        var value = node == null ? null : node.get(name);
        return value == null || value.isNull() ? null : value.asText();
    }

    public record Request(
            String resource,
            String query,
            String status,
            String visibility,
            String sort,
            String direction,
            List<String> fields) {
        private AdminOperationalQueryService.Request toQueryRequest() {
            return new AdminOperationalQueryService.Request(
                    1, MAX_ROWS, query, status, visibility, sort, direction);
        }
    }

    public record Created(long exportJobId) {}

    public record Status(
            long exportJobId,
            String resource,
            String status,
            java.time.Instant expiresAt,
            java.time.Instant completedAt,
            java.time.Instant downloadConsumedAt,
            String failureCode) {}
}
