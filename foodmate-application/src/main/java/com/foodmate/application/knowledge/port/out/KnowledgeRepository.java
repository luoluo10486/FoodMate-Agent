package com.foodmate.application.knowledge.port.out;

import com.foodmate.shared.knowledge.enums.KnowledgeDocumentStatus;

/** Knowledge persistence and operation-audit contract owned by the knowledge use cases. */
public interface KnowledgeRepository {
    void insertDocument(long documentId, String title, String storageKey, long operatorId);

    void updateDocumentSource(
            long documentId,
            String sourceType,
            String sourceName,
            String sourceVersion,
            String licenseNotice,
            long operatorId);

    int updateStatus(long documentId, KnowledgeDocumentStatus status, long operatorId);

    long nextAuditId();

    void insertAudit(Audit audit);

    void insertImportJob(ImportJob job);

    ImportJob findImportJob(long operatorId, String idempotencyKey);

    void insertImportItem(ImportItem item);

    void insertIndexOutbox(long outboxId, long itemId, String payload);

    int updateVisibility(long documentId, String visibility, long operatorId);

    void insertVisibilityOutbox(long outboxId, long documentId, String payload);

    java.util.List<OutboxRow> pendingIndexOutbox(int limit);

    java.util.List<OutboxRow> pendingVisibilityOutbox(int limit);

    int leaseIndexOutbox(long outboxId, String owner);

    int leaseVisibilityOutbox(long outboxId, String owner);

    void markIndexOutboxPublished(long outboxId, String owner);

    void markVisibilityOutboxPublished(long outboxId, String owner);

    void retryIndexOutbox(long outboxId, String owner, String error);

    void retryVisibilityOutbox(long outboxId, String owner, String error);

    void applyIndexResult(IndexResult result, String payloadHash);

    JobView job(long jobId);

    java.util.List<ItemView> jobItems(long jobId);

    java.util.List<JobEvent> jobEvents(long jobId, long afterEventId);

    int retryItem(long itemId, long jobId, long operatorId, long outboxId, String payload);

    record OutboxRow(long outboxId, long itemOrDocumentId, String topic, String payload) {}

    record IndexResult(
            long itemId,
            long documentId,
            String version,
            String status,
            int chunkCount,
            String errorCode,
            int attempt,
            long tokenCount,
            java.math.BigDecimal costAmount,
            String modelVersion) {}

    record JobView(long jobId, String status, int totalItems, int indexedItems, int failedItems) {}

    record ItemView(
            long itemId,
            long documentId,
            String filename,
            String uploadStatus,
            String indexStatus,
            int attempts,
            String errorCode) {}

    record JobEvent(long eventId, String eventType, String payload) {}

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
