package com.foodmate.application.knowledge.port.out;

import com.foodmate.shared.knowledge.enums.KnowledgeDocumentStatus;

/** Knowledge persistence and operation-audit contract owned by the knowledge use cases. */
public interface KnowledgeRepository {
    void insertDocument(long documentId, String title, String storageKey, long operatorId);

    int updateStatus(long documentId, KnowledgeDocumentStatus status, long operatorId);

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
