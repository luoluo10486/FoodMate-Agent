package com.foodmate.application.account;

public interface AdminManagementStore {
    int updateUserStatus(long userId, String status, long operatorId);

    int revokeSessions(long userId, long operatorId);

    int updateToolStatus(String name, String status, long operatorId);

    int updateKnowledgeStatus(long documentId, String status, long operatorId);

    int restore(String resourceType, long resourceId, long operatorId);

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
