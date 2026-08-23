package com.foodmate.application.retention.service;

import com.foodmate.shared.account.enums.UserRole;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** Governance workflow for retention and purge plans; it does not delete data itself. */
public interface DataRetentionService {
    PurgeResult requestPurge(PurgeCommand command);

    PurgeResult approvePurge(long requestId, ApprovalCommand command);

    HoldResult placeHold(HoldCommand command);

    HoldResult releaseHold(long holdId, ReleaseCommand command);

    PurgeResult getPurge(long requestId);

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
