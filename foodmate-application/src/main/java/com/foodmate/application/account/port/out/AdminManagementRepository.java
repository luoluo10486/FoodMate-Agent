package com.foodmate.application.account.port.out;

import com.foodmate.shared.account.enums.UserStatus;
import com.foodmate.shared.admin.enums.RestorableResourceType;
import com.foodmate.shared.runtime.enums.ToolStatus;

public interface AdminManagementRepository {
    int updateUserStatus(long userId, UserStatus status, long operatorId);

    int revokeSessions(long userId, long operatorId);

    int updateToolStatus(String name, ToolStatus status, long operatorId);

    int restore(RestorableResourceType resourceType, long resourceId, long operatorId);

    long nextAuditId();

    void insertAudit(Audit audit);

    record Audit(
            long id,
            long operatorId,
            String traceId,
            String targetType,
            String targetId,
            String action) {}
}
