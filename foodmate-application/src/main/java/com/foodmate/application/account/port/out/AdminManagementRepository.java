package com.foodmate.application.account.port.out;

import com.foodmate.shared.account.enums.UserStatus;
import com.foodmate.shared.admin.enums.RestorableResourceType;
import com.foodmate.shared.runtime.enums.ToolStatus;

public interface AdminManagementRepository {
    UserSnapshot findUser(long userId);

    ToolSnapshot findTool(String name);

    ResourceSnapshot findResource(RestorableResourceType resourceType, long resourceId);

    int updateUserStatus(long userId, UserStatus status, long operatorId, long revision);

    RevokeResult revokeSessions(long userId, long operatorId, long revision);

    int updateToolStatus(String name, ToolStatus status, long operatorId, long revision);

    int restore(
            RestorableResourceType resourceType, long resourceId, long operatorId, long revision);

    record UserSnapshot(long userId, String role, String status, long revision) {}

    record ToolSnapshot(String name, String riskLevel, String status, long revision) {}

    record ResourceSnapshot(String resourceType, long resourceId, long revision) {}

    record RevokeResult(int revoked, long revision) {}
}
