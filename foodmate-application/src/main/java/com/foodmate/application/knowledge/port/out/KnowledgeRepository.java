package com.foodmate.application.knowledge.port.out;

import com.foodmate.shared.knowledge.enums.KnowledgeDocumentStatus;

/** Knowledge persistence and operation-audit contract owned by the knowledge use cases. */
public interface KnowledgeRepository {
    void insertDocument(long documentId, String title, String storageKey, long operatorId);

    int updateStatus(long documentId, KnowledgeDocumentStatus status, long operatorId);

    long nextAuditId();

    void insertAudit(Audit audit);

    void insertImportJob(ImportJob job);

    void insertImportItem(ImportItem item);

    void insertIndexOutbox(long outboxId, long itemId, String payload);

    int updateVisibility(long documentId, String visibility, long operatorId);

    record ImportJob(
            long jobId,
            long operatorId,
            String idempotencyKey,
            String mode,
            String sourceType,
            String sourceName,
            String sourceVersion,
            String licenseNotice,
            String traceId) {}

    record ImportItem(
            long itemId,
            long jobId,
            long documentId,
            String filename,
            String contentType,
            long size) {}

    record Audit(
            long id,
            long operatorId,
            String traceId,
            String targetType,
            String targetId,
            String action) {}
}
