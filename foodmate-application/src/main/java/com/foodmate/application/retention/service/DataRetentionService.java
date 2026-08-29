package com.foodmate.application.retention.service;

import com.foodmate.shared.account.enums.UserRole;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/** 数据保留与清理计划的治理流程；本服务本身不直接删除数据。 */
public interface DataRetentionService {
    PurgeResult requestPurge(PurgeCommand command);

    PurgeResult approvePurge(long requestId, ApprovalCommand command);

    HoldResult placeHold(HoldCommand command);

    HoldResult releaseHold(long holdId, ReleaseCommand command);

    PurgeResult getPurge(long requestId);

    /** 返回不产生副作用且可安全展示的执行前置检查结果。 */
    PurgePreflight getPreflight(long requestId);

    static String purgeConfirmationDigest(String resourceType, long resourceId) {
        return sha256("retention.purge|" + resourceType + "|" + resourceId + "|1");
    }

    static String approvalConfirmationDigest(long requestId) {
        return sha256("retention.approve|" + requestId + "|1");
    }

    static String holdConfirmationDigest(String resourceType, long resourceId, String reasonCode) {
        return sha256(
                "retention.hold|" + resourceType + "|" + resourceId + "|" + reasonCode + "|1");
    }

    static String releaseConfirmationDigest(long holdId) {
        return sha256("retention.release|" + holdId + "|1");
    }

    record PurgeCommand(
            long operatorId,
            UserRole operatorRole,
            String traceId,
            String idempotencyKey,
            String resourceType,
            long resourceId,
            boolean confirmed,
            String confirmationDigest) {}

    record ApprovalCommand(
            long operatorId,
            UserRole operatorRole,
            String traceId,
            String idempotencyKey,
            boolean confirmed,
            String confirmationDigest) {}

    record HoldCommand(
            long operatorId,
            UserRole operatorRole,
            String traceId,
            String idempotencyKey,
            String resourceType,
            long resourceId,
            String reasonCode,
            boolean confirmed,
            String confirmationDigest) {}

    record ReleaseCommand(
            long operatorId,
            UserRole operatorRole,
            String traceId,
            String idempotencyKey,
            boolean confirmed,
            String confirmationDigest) {}

    record PurgeResult(
            long requestId,
            String status,
            String resourceType,
            long resourceId,
            java.time.Instant eligibleAt,
            int taskCount) {}

    record HoldResult(
            long holdId, String status, String resourceType, long resourceId, String reasonCode) {}

    record PurgePreflight(
            long requestId,
            String status,
            String resourceType,
            long resourceId,
            boolean policyFound,
            boolean hardDeleteEnabled,
            boolean resourceSoftDeleted,
            boolean retentionElapsed,
            boolean legalHoldClear,
            boolean taskContractValid,
            boolean readyToExecute,
            List<PurgeTaskState> tasks,
            List<String> blockers) {}

    record PurgeTaskState(String taskType, String status, int attemptCount, String lastErrorCode) {}

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
}
