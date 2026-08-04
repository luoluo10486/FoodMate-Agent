package com.foodmate.application.account.service.impl;

import com.foodmate.application.account.port.out.AdminManagementRepository;
import com.foodmate.application.account.service.AdminManagementService;
import com.foodmate.shared.account.enums.UserStatus;
import com.foodmate.shared.admin.enums.RestorableResourceType;
import com.foodmate.shared.runtime.enums.ToolStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 后台管理用例；权限由 API 层验证，持久化细节由 Infra 实现。 */
@Service
public class AdminManagementServiceImpl implements AdminManagementService {
    private final AdminManagementRepository store;

    public AdminManagementServiceImpl(AdminManagementRepository store) {
        this.store = store;
    }

    @Transactional
    public void updateUserStatus(long userId, UserStatus status, long operatorId, String traceId) {
        require(userId > 0 && status != null, "invalid user status");
        require(store.updateUserStatus(userId, status, operatorId) == 1, "user not found");
        audit(operatorId, traceId, "user.status.update", "user", Long.toString(userId));
    }

    @Transactional
    public int revokeSessions(long userId, long operatorId, String traceId) {
        int changed = store.revokeSessions(userId, operatorId);
        audit(
                operatorId,
                traceId,
                "user.sessions.revoke_all",
                "user_session",
                Long.toString(userId));
        return changed;
    }

    @Transactional
    public void updateToolStatus(String name, ToolStatus status, long operatorId, String traceId) {
        require(status != null, "invalid tool status");
        require(store.updateToolStatus(name, status, operatorId) == 1, "tool not found");
        audit(operatorId, traceId, "tool.status.update", "tool", name);
    }

    @Transactional
    public void restore(RestorableResourceType type, long id, long operatorId, String traceId) {
        require(type != null, "invalid resource type");
        require(store.restore(type, id, operatorId) == 1, "resource not found");
        audit(operatorId, traceId, "resource.restore", type.code(), Long.toString(id));
    }

    @Transactional
    public void recordAudit(
            long operatorId, String traceId, String action, String type, String id) {
        audit(operatorId, traceId, action, type, id);
    }

    private void audit(long operatorId, String traceId, String action, String type, String id) {
        store.insertAudit(
                new AdminManagementRepository.Audit(
                        store.nextAuditId(), operatorId, traceId, type, id, action));
    }

    private static void require(boolean ok, String message) {
        if (!ok) throw new IllegalArgumentException(message);
    }
}
