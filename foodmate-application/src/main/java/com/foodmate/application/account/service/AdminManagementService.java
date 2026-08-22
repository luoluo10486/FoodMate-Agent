package com.foodmate.application.account.service;

import com.foodmate.shared.account.enums.UserRole;
import com.foodmate.shared.account.enums.UserStatus;
import com.foodmate.shared.admin.enums.RestorableResourceType;
import com.foodmate.shared.runtime.enums.ToolStatus;

/** 后台管理用例接口。 */
public interface AdminManagementService {
    ManagementResult updateUserStatus(long userId, UserStatus status, AdminWriteCommand command);

    ManagementResult revokeSessions(long userId, AdminWriteCommand command);

    ManagementResult updateToolStatus(String name, ToolStatus status, AdminWriteCommand command);

    ManagementResult restore(RestorableResourceType type, long id, AdminWriteCommand command);

    record AdminWriteCommand(
            long operatorId,
            UserRole operatorRole,
            String traceId,
            String idempotencyKey,
            long revision,
            boolean confirmed,
            String confirmationDigest) {}

    record ManagementResult(boolean changed, String status, int affected, long revision) {}
}
