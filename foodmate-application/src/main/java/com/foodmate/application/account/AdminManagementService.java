package com.foodmate.application.account;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 后台管理用例；权限由 API 层验证，持久化细节由 Infra 实现。 */
@Service
public class AdminManagementService {
    private final AdminManagementStore store;

    public AdminManagementService(AdminManagementStore store) {
        this.store = store;
    }

    @Transactional
    public void updateUserStatus(long userId, String status, long operatorId, String traceId) {
        require(
                userId > 0
                        && ("active".equals(status)
                                || "disabled".equals(status)
                                || "locked".equals(status)),
                "invalid user status");
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
    public void updateToolStatus(String name, String status, long operatorId, String traceId) {
        require("active".equals(status) || "disabled".equals(status), "invalid tool status");
        require(store.updateToolStatus(name, status, operatorId) == 1, "tool not found");
        audit(operatorId, traceId, "tool.status.update", "tool", name);
    }

    @Transactional
    public void updateKnowledgeStatus(long id, String status, long operatorId, String traceId) {
        require(
                "uploaded".equals(status)
                        || "parsed".equals(status)
                        || "indexed".equals(status)
                        || "disabled".equals(status),
                "invalid document status");
        require(store.updateKnowledgeStatus(id, status, operatorId) == 1, "document not found");
        audit(
                operatorId,
                traceId,
                "knowledge.status.update",
                "knowledge_document",
                Long.toString(id));
    }

    @Transactional
    public void restore(String type, long id, long operatorId, String traceId) {
        require(store.restore(type, id, operatorId) == 1, "resource not found");
        audit(operatorId, traceId, "resource.restore", type, Long.toString(id));
    }

    @Transactional
    public void recordAudit(
            long operatorId, String traceId, String action, String type, String id) {
        audit(operatorId, traceId, action, type, id);
    }

    private void audit(long operatorId, String traceId, String action, String type, String id) {
        store.insertAudit(
                new AdminManagementStore.Audit(
                        store.nextAuditId(), operatorId, traceId, type, id, action));
    }

    private static void require(boolean ok, String message) {
        if (!ok) throw new IllegalArgumentException(message);
    }
}
