package com.foodmate.application.account.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodmate.application.account.port.out.AdminManagementRepository;
import com.foodmate.application.account.port.out.AdminManagementRepository.ResourceSnapshot;
import com.foodmate.application.account.port.out.AdminManagementRepository.ToolSnapshot;
import com.foodmate.application.account.port.out.AdminManagementRepository.UserSnapshot;
import com.foodmate.application.account.service.AdminManagementService;
import com.foodmate.application.account.service.AdminManagementService.AdminWriteCommand;
import com.foodmate.application.account.service.AdminManagementService.ManagementResult;
import com.foodmate.application.common.port.out.OperationAuditPort.IdempotencyRecord;
import com.foodmate.application.common.service.OperationAuditService;
import com.foodmate.shared.account.enums.UserRole;
import com.foodmate.shared.account.enums.UserStatus;
import com.foodmate.shared.admin.enums.RestorableResourceType;
import com.foodmate.shared.error.BusinessException;
import com.foodmate.shared.error.ErrorCode;
import com.foodmate.shared.runtime.enums.ToolStatus;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;

/** 后台管理写用例；权限、幂等、确认和并发校验均在 application 层执行。 */
@Service
public class AdminManagementServiceImpl implements AdminManagementService {
    private static final int MAX_IDEMPOTENCY_KEY_LENGTH = 128;

    private final AdminManagementRepository store;
    private final OperationAuditService audit;
    private final ObjectMapper mapper;

    public AdminManagementServiceImpl(
            AdminManagementRepository store, OperationAuditService audit, ObjectMapper mapper) {
        this.store = Objects.requireNonNull(store);
        this.audit = Objects.requireNonNull(audit);
        this.mapper = mapper.copy().findAndRegisterModules();
    }

    @Override
    @Transactional
    public ManagementResult updateUserStatus(
            long userId, UserStatus status, AdminWriteCommand command) {
        String action = "admin.user.status.update";
        String targetId = Long.toString(userId);
        String digest = digest(action, targetId, status == null ? null : status.code(), command);
        boolean reserved = false;
        try {
            validateCommand(command);
            requireAdmin(command);
            if (status == null || userId <= 0) throw invalid("invalid user status");

            IdempotencyRecord previous = existing(command, digest);
            if (previous != null) return replay(previous, digest);
            if (audit.reserve(
                            command.operatorId(),
                            "user",
                            targetId,
                            action,
                            digest,
                            command.idempotencyKey(),
                            Map.of("status", status.code(), "revision", command.revision()))
                    != 1) {
                previous = audit.findIdempotency(command.operatorId(), command.idempotencyKey());
                if (previous != null) return replay(previous, digest);
                throw conflict("幂等请求无法占用");
            }
            reserved = true;

            UserSnapshot current = requireUser(userId);
            if ("superadmin".equalsIgnoreCase(current.role())
                    && command.operatorRole() != UserRole.SUPERADMIN) {
                throw new BusinessException(ErrorCode.FORBIDDEN, "不能由 admin 修改 superadmin 账户");
            }
            requireRevision(current.revision(), command.revision(), "用户账户版本已变化");
            if (store.updateUserStatus(userId, status, command.operatorId(), command.revision())
                    != 1) throw conflict("用户账户状态已变化");

            ManagementResult result =
                    new ManagementResult(true, status.code(), 0, command.revision() + 1);
            audit.complete(command.operatorId(), command.idempotencyKey(), json(result));
            return result;
        } catch (RuntimeException exception) {
            recordFailureIfNeeded(reserved, command, "user", targetId, action, digest, exception);
            throw exception;
        }
    }

    @Override
    @Transactional
    public ManagementResult revokeSessions(long userId, AdminWriteCommand command) {
        String action = "admin.user.sessions.revoke_all";
        String targetId = Long.toString(userId);
        String digest = digest(action, targetId, null, command);
        boolean reserved = false;
        try {
            validateCommand(command);
            requireAdmin(command);
            if (userId <= 0) throw invalid("invalid user id");

            IdempotencyRecord previous = existing(command, digest);
            if (previous != null) return replay(previous, digest);
            if (audit.reserve(
                            command.operatorId(),
                            "user",
                            targetId,
                            action,
                            digest,
                            command.idempotencyKey(),
                            Map.of("revision", command.revision()))
                    != 1) {
                previous = audit.findIdempotency(command.operatorId(), command.idempotencyKey());
                if (previous != null) return replay(previous, digest);
                throw conflict("幂等请求无法占用");
            }
            reserved = true;

            UserSnapshot current = requireUser(userId);
            requireRevision(current.revision(), command.revision(), "用户账户版本已变化");
            AdminManagementRepository.RevokeResult revoked =
                    store.revokeSessions(userId, command.operatorId(), command.revision());
            if (revoked == null) throw conflict("用户账户状态已变化");

            ManagementResult result =
                    new ManagementResult(true, null, revoked.revoked(), revoked.revision());
            audit.complete(command.operatorId(), command.idempotencyKey(), json(result));
            return result;
        } catch (RuntimeException exception) {
            recordFailureIfNeeded(reserved, command, "user", targetId, action, digest, exception);
            throw exception;
        }
    }

    @Override
    @Transactional
    public ManagementResult updateToolStatus(
            String name, ToolStatus status, AdminWriteCommand command) {
        String action = "admin.tool.status.update";
        String targetId = name == null ? "" : name.trim();
        String digest = digest(action, targetId, status == null ? null : status.code(), command);
        boolean reserved = false;
        try {
            validateCommand(command);
            requireAdmin(command);
            if (targetId.isBlank() || status == null) throw invalid("invalid tool status");

            ToolSnapshot current = requireTool(targetId);
            if ("high".equalsIgnoreCase(current.riskLevel())) {
                if (command.operatorRole() != UserRole.SUPERADMIN)
                    throw new BusinessException(ErrorCode.FORBIDDEN, "高风险工具仅允许 superadmin 操作");
                requireConfirmation(
                        command,
                        confirmationDigest(action, targetId, status.code(), command.revision()));
            }

            IdempotencyRecord previous = existing(command, digest);
            if (previous != null) return replay(previous, digest);
            if (audit.reserve(
                            command.operatorId(),
                            "tool",
                            targetId,
                            action,
                            digest,
                            command.idempotencyKey(),
                            Map.of("status", status.code(), "revision", command.revision()))
                    != 1) {
                previous = audit.findIdempotency(command.operatorId(), command.idempotencyKey());
                if (previous != null) return replay(previous, digest);
                throw conflict("幂等请求无法占用");
            }
            reserved = true;

            requireRevision(current.revision(), command.revision(), "工具版本已变化");
            if (store.updateToolStatus(targetId, status, command.operatorId(), command.revision())
                    != 1) throw conflict("工具状态已变化");

            ManagementResult result =
                    new ManagementResult(true, status.code(), 0, command.revision() + 1);
            audit.complete(command.operatorId(), command.idempotencyKey(), json(result));
            return result;
        } catch (RuntimeException exception) {
            recordFailureIfNeeded(reserved, command, "tool", targetId, action, digest, exception);
            throw exception;
        }
    }

    @Override
    @Transactional
    public ManagementResult restore(
            RestorableResourceType type, long id, AdminWriteCommand command) {
        String action = "admin.resource.restore";
        String targetType = type == null ? "resource" : type.code();
        String targetId = Long.toString(id);
        String digest = digest(action, targetType + ":" + targetId, null, command);
        boolean reserved = false;
        try {
            validateCommand(command);
            requireAdmin(command);
            if (type == null || id <= 0) throw invalid("invalid resource");
            requireConfirmation(
                    command, confirmationDigest(action, targetType, targetId, command.revision()));

            IdempotencyRecord previous = existing(command, digest);
            if (previous != null) return replay(previous, digest);
            if (audit.reserve(
                            command.operatorId(),
                            targetType,
                            targetId,
                            action,
                            digest,
                            command.idempotencyKey(),
                            Map.of("resource_type", targetType, "revision", command.revision()))
                    != 1) {
                previous = audit.findIdempotency(command.operatorId(), command.idempotencyKey());
                if (previous != null) return replay(previous, digest);
                throw conflict("幂等请求无法占用");
            }
            reserved = true;

            ResourceSnapshot current = store.findResource(type, id);
            if (current == null) throw new BusinessException(ErrorCode.NOT_FOUND, "资源不存在");
            requireRevision(current.revision(), command.revision(), "资源版本已变化");
            if (store.restore(type, id, command.operatorId(), command.revision()) != 1)
                throw conflict("资源状态已变化");

            ManagementResult result = new ManagementResult(true, null, 0, command.revision() + 1);
            audit.complete(command.operatorId(), command.idempotencyKey(), json(result));
            return result;
        } catch (RuntimeException exception) {
            recordFailureIfNeeded(
                    reserved, command, targetType, targetId, action, digest, exception);
            throw exception;
        }
    }

    private UserSnapshot requireUser(long userId) {
        UserSnapshot value = store.findUser(userId);
        if (value == null) throw new BusinessException(ErrorCode.NOT_FOUND, "用户不存在");
        return value;
    }

    private ToolSnapshot requireTool(String name) {
        ToolSnapshot value = store.findTool(name);
        if (value == null) throw new BusinessException(ErrorCode.NOT_FOUND, "工具不存在");
        return value;
    }

    private IdempotencyRecord existing(AdminWriteCommand command, String digest) {
        IdempotencyRecord previous =
                audit.findIdempotency(command.operatorId(), command.idempotencyKey());
        if (previous != null && !digest.equals(previous.parametersDigest()))
            throw conflict("幂等键对应的请求参数已变化");
        return previous;
    }

    private ManagementResult replay(IdempotencyRecord previous, String digest) {
        if (!digest.equals(previous.parametersDigest())) throw conflict("幂等键对应的请求参数已变化");
        if (!"success".equalsIgnoreCase(previous.result())) throw conflict("幂等请求正在处理中或已失败");
        return parseResult(previous.responseJson());
    }

    private void recordFailureIfNeeded(
            boolean reserved,
            AdminWriteCommand command,
            String targetType,
            String targetId,
            String action,
            String digest,
            RuntimeException exception) {
        if (command == null) return;
        if (command.operatorId() <= 0
                || command.idempotencyKey() == null
                || command.idempotencyKey().isBlank()
                || command.idempotencyKey().length() > MAX_IDEMPOTENCY_KEY_LENGTH) return;
        String code = errorCode(exception);
        if (reserved) {
            recordFailureAfterRollback(command, targetType, targetId, action, digest, code);
            return;
        }
        if (audit.findIdempotency(command.operatorId(), command.idempotencyKey()) != null) return;
        audit.recordFailure(
                command.operatorId(),
                targetType,
                targetId,
                action,
                "failed",
                code,
                digest,
                command.idempotencyKey(),
                Map.of("failure", "admin_management"));
    }

    private void recordFailureAfterRollback(
            AdminWriteCommand command,
            String targetType,
            String targetId,
            String action,
            String digest,
            String errorCode) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            audit.recordFailure(
                    command.operatorId(),
                    targetType,
                    targetId,
                    action,
                    "failed",
                    errorCode,
                    digest,
                    command.idempotencyKey(),
                    Map.of("failure", "admin_management"));
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (status == STATUS_ROLLED_BACK)
                            audit.recordFailure(
                                    command.operatorId(),
                                    targetType,
                                    targetId,
                                    action,
                                    "failed",
                                    errorCode,
                                    digest,
                                    command.idempotencyKey(),
                                    Map.of("failure", "admin_management"));
                    }
                });
    }

    private static void validateCommand(AdminWriteCommand command) {
        if (command == null || command.operatorId() <= 0 || command.operatorRole() == null)
            throw invalid("管理操作上下文无效");
        if (command.idempotencyKey() == null
                || command.idempotencyKey().isBlank()
                || command.idempotencyKey().length() > MAX_IDEMPOTENCY_KEY_LENGTH)
            throw invalid("Idempotency-Key 必须为 1-128 个字符");
        if (command.revision() < 1) throw invalid("revision 必须大于 0");
    }

    private static void requireAdmin(AdminWriteCommand command) {
        if (command.operatorRole() != UserRole.ADMIN
                && command.operatorRole() != UserRole.SUPERADMIN)
            throw new BusinessException(ErrorCode.FORBIDDEN, "insufficient role");
    }

    private static void requireRevision(long actual, long expected, String message) {
        if (actual != expected) throw conflict(message);
    }

    private static void requireConfirmation(AdminWriteCommand command, String expectedDigest) {
        if (!command.confirmed()
                || command.confirmationDigest() == null
                || !MessageDigest.isEqual(
                        expectedDigest.getBytes(StandardCharsets.UTF_8),
                        command.confirmationDigest().getBytes(StandardCharsets.UTF_8)))
            throw new BusinessException(ErrorCode.CONFLICT, "需要匹配的确认摘要");
    }

    private String json(ManagementResult result) {
        try {
            return mapper.writeValueAsString(
                    Map.of(
                            "changed", result.changed(),
                            "status", result.status() == null ? "" : result.status(),
                            "affected", result.affected(),
                            "revision", result.revision()));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("cannot serialize management result", exception);
        }
    }

    private ManagementResult parseResult(String value) {
        try {
            JsonNode result = mapper.readTree(value == null ? "{}" : value);
            String status = result.path("status").asText("");
            return new ManagementResult(
                    result.path("changed").asBoolean(true),
                    status.isBlank() ? null : status,
                    result.path("affected").asInt(),
                    result.path("revision").asLong());
        } catch (JsonProcessingException exception) {
            throw conflict("幂等结果摘要无效");
        }
    }

    private static String digest(
            String action, String target, String value, AdminWriteCommand command) {
        return sha256(
                action
                        + "|"
                        + target
                        + "|"
                        + value
                        + "|"
                        + (command == null ? "" : command.revision())
                        + "|"
                        + (command != null && command.confirmed())
                        + "|"
                        + (command == null ? "" : command.confirmationDigest()));
    }

    /** 客户端可据此生成高风险管理操作的确认摘要。 */
    public static String confirmationDigest(
            String action, String target, String value, long revision) {
        return sha256(action + "|" + target + "|" + value + "|" + revision);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String errorCode(RuntimeException exception) {
        if (exception instanceof BusinessException businessException)
            return businessException.errorCode().code();
        return ErrorCode.INTERNAL_ERROR.code();
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(ErrorCode.INVALID_ARGUMENT, message);
    }

    private static BusinessException conflict(String message) {
        return new BusinessException(ErrorCode.CONFLICT, message);
    }
}
