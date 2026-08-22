package com.foodmate.application.retention.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.foodmate.application.common.port.out.OperationAuditPort.IdempotencyRecord;
import com.foodmate.application.common.service.OperationAuditService;
import com.foodmate.application.retention.port.out.DataRetentionRepository;
import com.foodmate.application.retention.port.out.DataRetentionRepository.Hold;
import com.foodmate.application.retention.port.out.DataRetentionRepository.Policy;
import com.foodmate.application.retention.port.out.DataRetentionRepository.PurgeRequest;
import com.foodmate.application.retention.port.out.DataRetentionRepository.ResourceSnapshot;
import com.foodmate.application.retention.service.DataRetentionService;
import com.foodmate.shared.account.enums.UserRole;
import com.foodmate.shared.error.BusinessException;
import com.foodmate.shared.error.ErrorCode;
import com.foodmate.shared.id.IdGenerator;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Retention governance; approval creates a plan but does not execute hard deletion. */
@Service
public class DataRetentionServiceImpl implements DataRetentionService {
    private static final String PURGE_ACTION = "retention.purge.request";
    private static final String APPROVE_ACTION = "retention.purge.approve";
    private static final String HOLD_ACTION = "retention.hold.place";
    private static final String RELEASE_ACTION = "retention.hold.release";
    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 128;
    private static final int MAX_REASON_LENGTH = 64;
    private static final Set<String> RESOURCE_TYPES =
            Set.of("knowledge_document", "admin_export_job");

    private final DataRetentionRepository store;
    private final OperationAuditService audit;
    private final IdGenerator ids;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final String bucket;

    /** Compatibility constructor for application-only tests. */
    public DataRetentionServiceImpl(
            DataRetentionRepository store, OperationAuditService audit, IdGenerator ids) {
        this(store, audit, ids, Clock.systemUTC(), "foodmate-private");
    }

    public DataRetentionServiceImpl(
            DataRetentionRepository store,
            OperationAuditService audit,
            IdGenerator ids,
            Clock clock,
            String bucket) {
        this.store = Objects.requireNonNull(store);
        this.audit = Objects.requireNonNull(audit);
        this.ids = Objects.requireNonNull(ids);
        this.mapper =
                new ObjectMapper()
                        .findAndRegisterModules()
                        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.clock = Objects.requireNonNull(clock);
        this.bucket = requireBucket(bucket);
    }

    @Autowired
    public DataRetentionServiceImpl(
            DataRetentionRepository store,
            OperationAuditService audit,
            IdGenerator ids,
            @Value("${foodmate.storage.bucket:foodmate-private}") String bucket) {
        this(store, audit, ids, Clock.systemUTC(), bucket);
    }

    @Override
    @Transactional
    public PurgeResult requestPurge(PurgeCommand command) {
        String resourceType = normalizeResource(command == null ? null : command.resourceType());
        String targetId = Long.toString(command == null ? 0 : command.resourceId());
        String digest = digest(PURGE_ACTION, resourceType, targetId);
        boolean reserved = false;
        try {
            validatePurgeCommand(command, resourceType);
            requireAdmin(command.operatorRole());
            IdempotencyRecord previous =
                    audit.findIdempotency(command.operatorId(), command.idempotencyKey());
            if (previous != null) return replayPurge(previous, digest);

            ResourceSnapshot resource = requireResource(resourceType, command.resourceId());
            Policy policy = requirePolicy(resourceType);
            Instant eligibleAt = eligibleAt(resource, policy);
            if (eligibleAt.isAfter(Instant.now(clock)))
                throw new BusinessException(ErrorCode.RETENTION_NOT_ELIGIBLE);
            requireNoHold(resourceType, command.resourceId());
            if (store.activePurgeRequest(resourceType, command.resourceId()) != null)
                throw new BusinessException(ErrorCode.RETENTION_REQUEST_ACTIVE);

            if (audit.reserve(
                            command.operatorId(),
                            resourceType,
                            targetId,
                            PURGE_ACTION,
                            digest,
                            command.idempotencyKey(),
                            Map.of("resource_type", resourceType, "confirmed", "true"))
                    != 1) {
                previous = audit.findIdempotency(command.operatorId(), command.idempotencyKey());
                if (previous != null) return replayPurge(previous, digest);
                throw new BusinessException(ErrorCode.CONFLICT, "幂等请求无法占用");
            }
            reserved = true;
            long requestId = ids.nextId();
            if (store.insertPurgeRequest(
                            new DataRetentionRepository.NewPurgeRequest(
                                    requestId,
                                    resourceType,
                                    command.resourceId(),
                                    policy.policyId(),
                                    command.operatorId(),
                                    command.idempotencyKey(),
                                    resource.deletedAt(),
                                    eligibleAt))
                    != 1) throw new BusinessException(ErrorCode.RETENTION_REQUEST_ACTIVE);
            PurgeResult result =
                    new PurgeResult(
                            requestId,
                            "requested",
                            resourceType,
                            command.resourceId(),
                            eligibleAt,
                            0);
            audit.complete(command.operatorId(), command.idempotencyKey(), json(result));
            return result;
        } catch (RuntimeException exception) {
            if (reserved)
                recordFailureAfterRollback(
                        command.operatorId(),
                        resourceType,
                        targetId,
                        PURGE_ACTION,
                        digest,
                        command.idempotencyKey(),
                        errorCode(exception));
            throw exception;
        }
    }

    @Override
    @Transactional
    public PurgeResult approvePurge(long requestId, ApprovalCommand command) {
        String targetId = Long.toString(requestId);
        String digest = digest(APPROVE_ACTION, targetId, "");
        boolean reserved = false;
        try {
            validateApprovalCommand(command, requestId);
            requireSuperadmin(command.operatorRole());
            PurgeRequest request = requirePurge(requestId);
            if (!"requested".equals(request.status()))
                throw new BusinessException(ErrorCode.RETENTION_REQUEST_NOT_APPROVABLE);
            if (request.requestedBy() == command.operatorId())
                throw new BusinessException(
                        ErrorCode.RETENTION_APPROVAL_REQUIRED, "申请人不能审批自己的清理请求");
            ResourceSnapshot resource =
                    requireResource(request.resourceType(), request.resourceId());
            Policy policy = requirePolicy(request.resourceType());
            if (eligibleAt(resource, policy).isAfter(Instant.now(clock)))
                throw new BusinessException(ErrorCode.RETENTION_NOT_ELIGIBLE);
            requireNoHold(request.resourceType(), request.resourceId());
            IdempotencyRecord previous =
                    audit.findIdempotency(command.operatorId(), command.idempotencyKey());
            if (previous != null) return replayPurge(previous, digest);
            if (audit.reserve(
                            command.operatorId(),
                            "data_purge_request",
                            targetId,
                            APPROVE_ACTION,
                            digest,
                            command.idempotencyKey(),
                            Map.of("resource_type", request.resourceType(), "confirmed", "true"))
                    != 1) {
                previous = audit.findIdempotency(command.operatorId(), command.idempotencyKey());
                if (previous != null) return replayPurge(previous, digest);
                throw new BusinessException(ErrorCode.CONFLICT, "幂等请求无法占用");
            }
            reserved = true;
            if (store.approvePurge(requestId, command.operatorId(), Instant.now(clock)) != 1)
                throw new BusinessException(ErrorCode.RETENTION_REQUEST_NOT_APPROVABLE);
            int taskCount = planTasks(request, resource);
            PurgeResult result =
                    new PurgeResult(
                            requestId,
                            "approved",
                            request.resourceType(),
                            request.resourceId(),
                            request.eligibleAt(),
                            taskCount);
            audit.complete(command.operatorId(), command.idempotencyKey(), json(result));
            return result;
        } catch (RuntimeException exception) {
            if (reserved)
                recordFailureAfterRollback(
                        command.operatorId(),
                        "data_purge_request",
                        targetId,
                        APPROVE_ACTION,
                        digest,
                        command.idempotencyKey(),
                        errorCode(exception));
            throw exception;
        }
    }

    @Override
    @Transactional
    public HoldResult placeHold(HoldCommand command) {
        String resourceType = normalizeResource(command == null ? null : command.resourceType());
        String targetId = Long.toString(command == null ? 0 : command.resourceId());
        String reasonCode = normalizeReason(command == null ? null : command.reasonCode());
        String digest = digest(HOLD_ACTION, resourceType + ":" + targetId, reasonCode);
        boolean reserved = false;
        try {
            validateHoldCommand(command, resourceType, reasonCode);
            requireAdmin(command.operatorRole());
            requireExistingResource(resourceType, command.resourceId());
            IdempotencyRecord previous =
                    audit.findIdempotency(command.operatorId(), command.idempotencyKey());
            if (previous != null) return replayHold(previous, digest);
            if (store.activeHold(resourceType, command.resourceId()) != null)
                throw new BusinessException(ErrorCode.RETENTION_HOLD_ACTIVE);
            if (audit.reserve(
                            command.operatorId(),
                            resourceType,
                            targetId,
                            HOLD_ACTION,
                            digest,
                            command.idempotencyKey(),
                            Map.of("resource_type", resourceType, "reason_code", reasonCode))
                    != 1) {
                previous = audit.findIdempotency(command.operatorId(), command.idempotencyKey());
                if (previous != null) return replayHold(previous, digest);
                throw new BusinessException(ErrorCode.CONFLICT, "幂等请求无法占用");
            }
            reserved = true;
            long holdId = ids.nextId();
            if (store.insertHold(
                            new DataRetentionRepository.NewHold(
                                    holdId,
                                    resourceType,
                                    command.resourceId(),
                                    reasonCode,
                                    command.operatorId()))
                    != 1) throw new BusinessException(ErrorCode.RETENTION_HOLD_ACTIVE);
            HoldResult result =
                    new HoldResult(
                            holdId, "active", resourceType, command.resourceId(), reasonCode);
            audit.complete(command.operatorId(), command.idempotencyKey(), json(result));
            return result;
        } catch (RuntimeException exception) {
            if (reserved)
                recordFailureAfterRollback(
                        command.operatorId(),
                        resourceType,
                        targetId,
                        HOLD_ACTION,
                        digest,
                        command.idempotencyKey(),
                        errorCode(exception));
            throw exception;
        }
    }

    @Override
    @Transactional
    public HoldResult releaseHold(long holdId, ReleaseCommand command) {
        String targetId = Long.toString(holdId);
        String digest = digest(RELEASE_ACTION, targetId, "");
        boolean reserved = false;
        try {
            validateReleaseCommand(command, holdId);
            requireSuperadmin(command.operatorRole());
            Hold hold = store.hold(holdId);
            if (hold == null || !"active".equals(hold.status()))
                throw new BusinessException(ErrorCode.RETENTION_HOLD_NOT_FOUND);
            IdempotencyRecord previous =
                    audit.findIdempotency(command.operatorId(), command.idempotencyKey());
            if (previous != null) return replayHold(previous, digest);
            if (audit.reserve(
                            command.operatorId(),
                            "data_retention_hold",
                            targetId,
                            RELEASE_ACTION,
                            digest,
                            command.idempotencyKey(),
                            Map.of("resource_type", hold.resourceType()))
                    != 1) {
                previous = audit.findIdempotency(command.operatorId(), command.idempotencyKey());
                if (previous != null) return replayHold(previous, digest);
                throw new BusinessException(ErrorCode.CONFLICT, "幂等请求无法占用");
            }
            reserved = true;
            if (store.releaseHold(holdId, command.operatorId(), Instant.now(clock)) != 1)
                throw new BusinessException(ErrorCode.RETENTION_HOLD_NOT_FOUND);
            HoldResult result =
                    new HoldResult(
                            holdId,
                            "released",
                            hold.resourceType(),
                            hold.resourceId(),
                            hold.reasonCode());
            audit.complete(command.operatorId(), command.idempotencyKey(), json(result));
            return result;
        } catch (RuntimeException exception) {
            if (reserved)
                recordFailureAfterRollback(
                        command.operatorId(),
                        "data_retention_hold",
                        targetId,
                        RELEASE_ACTION,
                        digest,
                        command.idempotencyKey(),
                        errorCode(exception));
            throw exception;
        }
    }

    @Override
    public PurgeResult getPurge(long requestId) {
        PurgeRequest request = requirePurge(requestId);
        return new PurgeResult(
                request.requestId(),
                request.status(),
                request.resourceType(),
                request.resourceId(),
                request.eligibleAt(),
                request.taskCount());
    }

    private int planTasks(PurgeRequest request, ResourceSnapshot resource) {
        int count = 0;
        if (resource.storageKey() != null && !resource.storageKey().isBlank()) {
            if (store.insertPurgeTask(
                            new DataRetentionRepository.PurgeTask(
                                    ids.nextId(),
                                    request.requestId(),
                                    "object_storage",
                                    null,
                                    objectTarget(resource.storageKey())))
                    != 1)
                throw new IllegalStateException("retention object task was not persisted");
            count++;
        }
        if ("knowledge_document".equals(request.resourceType())) {
            if (store.insertPurgeTask(
                            new DataRetentionRepository.PurgeTask(
                                    ids.nextId(),
                                    request.requestId(),
                                    "vector_index",
                                    "foodmate-knowledge-purge-v1",
                                    vectorTarget(request.resourceId(), resource.version())))
                    != 1)
                throw new IllegalStateException("retention vector task was not persisted");
            count++;
        }
        if (store.insertPurgeTask(
                        new DataRetentionRepository.PurgeTask(
                                ids.nextId(),
                                request.requestId(),
                                "database",
                                null,
                                databaseTarget(request.resourceType(), request.resourceId())))
                != 1) throw new IllegalStateException("retention database task was not persisted");
        return count + 1;
    }

    private ResourceSnapshot requireResource(String resourceType, long resourceId) {
        if (resourceId <= 0) throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "资源 ID 无效");
        ResourceSnapshot resource = store.resource(resourceType, resourceId);
        if (resource == null) throw new BusinessException(ErrorCode.NOT_FOUND, "资源不存在");
        if (!resource.deleted() || resource.deletedAt() == null)
            throw new BusinessException(ErrorCode.RETENTION_NOT_ELIGIBLE);
        return resource;
    }

    private ResourceSnapshot requireExistingResource(String resourceType, long resourceId) {
        if (resourceId <= 0) throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "资源 ID 无效");
        ResourceSnapshot resource = store.resource(resourceType, resourceId);
        if (resource == null) throw new BusinessException(ErrorCode.NOT_FOUND, "资源不存在");
        return resource;
    }

    private Policy requirePolicy(String resourceType) {
        Policy policy = store.policy(resourceType);
        if (policy == null || policy.retentionDays() < 0)
            throw new BusinessException(ErrorCode.RETENTION_POLICY_NOT_FOUND);
        return policy;
    }

    private Instant eligibleAt(ResourceSnapshot resource, Policy policy) {
        return resource.deletedAt().plusSeconds(policy.retentionDays() * 86400L);
    }

    private void requireNoHold(String resourceType, long resourceId) {
        if (store.activeHold(resourceType, resourceId) != null)
            throw new BusinessException(ErrorCode.RETENTION_HOLD_ACTIVE);
    }

    private PurgeRequest requirePurge(long requestId) {
        PurgeRequest request = store.purgeRequest(requestId);
        if (request == null) throw new BusinessException(ErrorCode.NOT_FOUND, "清理请求不存在");
        return request;
    }

    private PurgeResult replayPurge(IdempotencyRecord previous, String digest) {
        if (!digest.equals(previous.parametersDigest())
                || !"success".equalsIgnoreCase(previous.result()))
            throw new BusinessException(ErrorCode.CONFLICT, "幂等键对应的请求参数已变化或仍在处理中");
        try {
            return mapper.readValue(previous.responseJson(), PurgeResult.class);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "清理审计结果无效");
        }
    }

    private HoldResult replayHold(IdempotencyRecord previous, String digest) {
        if (!digest.equals(previous.parametersDigest())
                || !"success".equalsIgnoreCase(previous.result()))
            throw new BusinessException(ErrorCode.CONFLICT, "幂等键对应的请求参数已变化或仍在处理中");
        try {
            return mapper.readValue(previous.responseJson(), HoldResult.class);
        } catch (JsonProcessingException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "冻结审计结果无效");
        }
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("cannot serialize retention result", exception);
        }
    }

    private String objectTarget(String key) {
        return "{\"bucket\":\"" + escape(bucket) + "\",\"key\":\"" + escape(key) + "\"}";
    }

    private String vectorTarget(long documentId, String version) {
        return "{\"document_id\":" + documentId + ",\"version\":\"" + escape(version) + "\"}";
    }

    private String databaseTarget(String resourceType, long resourceId) {
        return "{\"resource_type\":\""
                + escape(resourceType)
                + "\",\"resource_id\":"
                + resourceId
                + "}";
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String digest(String action, String target, String value) {
        try {
            return "sha256:"
                    + java.util.HexFormat.of()
                            .formatHex(
                                    MessageDigest.getInstance("SHA-256")
                                            .digest(
                                                    (action + "|" + target + "|" + value)
                                                            .getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private void recordFailureAfterRollback(
            long operatorId,
            String targetType,
            String targetId,
            String action,
            String parametersDigest,
            String idempotencyKey,
            String errorCode) {
        Runnable record =
                () ->
                        audit.recordFailure(
                                operatorId,
                                targetType,
                                targetId,
                                action,
                                "failed",
                                errorCode,
                                parametersDigest,
                                idempotencyKey,
                                Map.of("failure", action));
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            record.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (status == STATUS_ROLLED_BACK) record.run();
                    }
                });
    }

    private static void validatePurgeCommand(PurgeCommand command, String resourceType) {
        if (command == null
                || command.operatorId() <= 0
                || command.resourceId() <= 0
                || !RESOURCE_TYPES.contains(resourceType))
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "清理请求参数无效");
        validateIdempotency(command.idempotencyKey());
        if (!command.confirmed()
                || !MessageDigest.isEqual(
                        DataRetentionService.purgeConfirmationDigest(
                                        resourceType, command.resourceId())
                                .getBytes(StandardCharsets.UTF_8),
                        safe(command.confirmationDigest()).getBytes(StandardCharsets.UTF_8)))
            throw new BusinessException(ErrorCode.CONFLICT, "需要匹配的清理确认摘要");
    }

    private static void validateApprovalCommand(ApprovalCommand command, long requestId) {
        if (command == null || command.operatorId() <= 0 || requestId <= 0)
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "审批请求参数无效");
        validateIdempotency(command.idempotencyKey());
        if (!command.confirmed()
                || !MessageDigest.isEqual(
                        DataRetentionService.approvalConfirmationDigest(requestId)
                                .getBytes(StandardCharsets.UTF_8),
                        safe(command.confirmationDigest()).getBytes(StandardCharsets.UTF_8)))
            throw new BusinessException(ErrorCode.CONFLICT, "需要匹配的审批确认摘要");
    }

    private static void validateHoldCommand(
            HoldCommand command, String resourceType, String reasonCode) {
        if (command == null
                || command.operatorId() <= 0
                || command.resourceId() <= 0
                || !RESOURCE_TYPES.contains(resourceType)
                || reasonCode == null)
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "冻结请求参数无效");
        validateIdempotency(command.idempotencyKey());
        if (!command.confirmed()
                || !MessageDigest.isEqual(
                        DataRetentionService.holdConfirmationDigest(
                                        resourceType, command.resourceId(), reasonCode)
                                .getBytes(StandardCharsets.UTF_8),
                        safe(command.confirmationDigest()).getBytes(StandardCharsets.UTF_8)))
            throw new BusinessException(ErrorCode.CONFLICT, "需要匹配的冻结确认摘要");
    }

    private static void validateReleaseCommand(ReleaseCommand command, long holdId) {
        if (command == null || command.operatorId() <= 0 || holdId <= 0)
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "解冻请求参数无效");
        validateIdempotency(command.idempotencyKey());
        if (!command.confirmed()
                || !MessageDigest.isEqual(
                        DataRetentionService.releaseConfirmationDigest(holdId)
                                .getBytes(StandardCharsets.UTF_8),
                        safe(command.confirmationDigest()).getBytes(StandardCharsets.UTF_8)))
            throw new BusinessException(ErrorCode.CONFLICT, "需要匹配的解冻确认摘要");
    }

    private static void validateIdempotency(String value) {
        if (value == null || value.isBlank() || value.length() > MAX_IDEMPOTENCY_KEY_LENGTH)
            throw new BusinessException(
                    ErrorCode.INVALID_ARGUMENT, "Idempotency-Key 必须为 1-128 个字符");
    }

    private static void requireAdmin(UserRole role) {
        if (role != UserRole.ADMIN && role != UserRole.SUPERADMIN)
            throw new BusinessException(ErrorCode.FORBIDDEN, "insufficient role");
    }

    private static void requireSuperadmin(UserRole role) {
        if (role != UserRole.SUPERADMIN)
            throw new BusinessException(ErrorCode.RETENTION_APPROVAL_REQUIRED);
    }

    private static String normalizeResource(String value) {
        return value == null ? "" : value.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static String normalizeReason(String value) {
        if (value == null) return null;
        String normalized = value.trim().toLowerCase(java.util.Locale.ROOT);
        return normalized.isBlank()
                        || normalized.length() > MAX_REASON_LENGTH
                        || !normalized.matches("[a-z0-9_.-]+")
                ? null
                : normalized;
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static String errorCode(RuntimeException exception) {
        return exception instanceof BusinessException businessException
                ? businessException.errorCode().code()
                : ErrorCode.INTERNAL_ERROR.code();
    }

    private static String requireBucket(String value) {
        if (value == null || value.isBlank() || !value.matches("[A-Za-z0-9._-]+"))
            throw new IllegalArgumentException("storage bucket is invalid");
        return value;
    }
}
