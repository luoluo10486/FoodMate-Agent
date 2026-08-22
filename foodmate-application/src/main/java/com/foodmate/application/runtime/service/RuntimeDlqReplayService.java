package com.foodmate.application.runtime.service;

import com.foodmate.shared.account.enums.UserRole;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** 管理员发起的、可审计的 DLQ 事件重放用例。 */
public interface RuntimeDlqReplayService {
    ReplayResult request(long dlqId, Command command);

    /** 返回前端确认对话框使用的稳定摘要。 */
    static String confirmationDigest(long dlqId) {
        return sha256("runtime.dlq.replay|" + dlqId + "||1");
    }

    record Command(
            long operatorId,
            UserRole operatorRole,
            String idempotencyKey,
            boolean confirmed,
            String confirmationDigest) {}

    record ReplayResult(long replayId, long dlqId, String status, String originalMessageId) {}

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
