package com.foodmate.application.account.service;

import com.foodmate.shared.account.enums.UserStatus;
import com.foodmate.shared.admin.enums.RestorableResourceType;
import com.foodmate.shared.runtime.enums.ToolStatus;

/** 后台管理用例接口。 */
public interface AdminManagementService {
    void updateUserStatus(long userId, UserStatus status, long operatorId, String traceId);

    int revokeSessions(long userId, long operatorId, String traceId);

    void updateToolStatus(String name, ToolStatus status, long operatorId, String traceId);

    void restore(RestorableResourceType type, long id, long operatorId, String traceId);

    void recordAudit(long operatorId, String traceId, String action, String type, String id);
}
