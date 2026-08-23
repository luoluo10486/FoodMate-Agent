package com.foodmate.application.knowledge.port.out;

import com.foodmate.shared.knowledge.enums.KnowledgeDocumentStatus;
import java.math.BigDecimal;
import java.util.List;

/** Knowledge persistence and operation-audit contract owned by the knowledge use cases. */
public interface KnowledgeRepository {
    /** Persists the initial document fact before indexing begins. */
    void insertDocument(long documentId, String title, String storageKey, long operatorId);

    /** Persists source and licensing metadata without storing document contents. */
    void updateDocumentSource(
            long documentId,
            String sourceType,
            String sourceName,
            String sourceVersion,
            String licenseNotice,
            long operatorId);

    /** Applies an allowed document lifecycle transition. */
    int updateStatus(long documentId, KnowledgeDocumentStatus status, long operatorId);

    /** Allocates an audit identifier from the shared ID generator boundary. */
    long nextAuditId();

    /** Persists a knowledge operation audit fact in the current transaction. */
    void insertAudit(Audit audit);

    /** Persists an import job fact. */
    void insertImportJob(ImportJob job);

    /** Finds an existing import job for operator-scoped idempotency replay. */
    ImportJob findImportJob(long operatorId, String idempotencyKey);

    /** Persists one file item belonging to an import job. */
    void insertImportItem(ImportItem item);

    /** Persists the committed index request consumed by the Runtime worker. */
    void insertIndexOutbox(long outboxId, long itemId, String payload);

    /** Applies a document visibility transition in the authoritative store. */
    int updateVisibility(long documentId, String visibility, long operatorId);

    /** Reads the authoritative document version used by lifecycle commands. */
    DocumentView document(long documentId);

    /** Rechecks the authoritative PostgreSQL visibility and version gate. */
    default boolean isPublicPublished(long documentId, String version) {
        return false;
    }

    /** Persists a replayable visibility projection request. */
    void insertVisibilityOutbox(long outboxId, long documentId, String payload);

    /** Reads pending index messages eligible for leasing. */
    List<OutboxRow> pendingIndexOutbox(int limit);

    /** Reads pending visibility messages eligible for leasing. */
    List<OutboxRow> pendingVisibilityOutbox(int limit);

    /** Claims one index message for a bounded publication lease. */
    int leaseIndexOutbox(long outboxId, String owner);

    /** Claims one visibility message for a bounded publication lease. */
    int leaseVisibilityOutbox(long outboxId, String owner);

    /** Records a successful index publication owned by the lease holder. */
    void markIndexOutboxPublished(long outboxId, String owner);

    /** Records a successful visibility publication owned by the lease holder. */
    void markVisibilityOutboxPublished(long outboxId, String owner);

    /** Schedules a failed index publication for retry. */
    void retryIndexOutbox(long outboxId, String owner, String error);

    /** Schedules a failed visibility publication for retry. */
    void retryVisibilityOutbox(long outboxId, String owner, String error);

    /** Applies one index result idempotently and updates the batch read model. */
    void applyIndexResult(IndexResult result, String payloadHash);

    /** Replaces the current version's authoritative chunk facts in the same transaction. */
    default void replaceKnowledgeChunks(IndexResult result) {
        // Local stub persistence does not own a database chunk table.
    }

    /** Reads batch-level progress. */
    JobView job(long jobId);

    /** Reads file-level progress for a batch. */
    List<ItemView> jobItems(long jobId);

    /** Reads batch events after a resumable SSE cursor. */
    List<JobEvent> jobEvents(long jobId, long afterEventId);

    /** Finds the owning batch for an imported file item. */
    long jobIdForItem(long itemId);

    /** Persists one replayable batch progress event. */
    void insertJobEvent(long eventId, long jobId, Long itemId, String eventType, String payload);

    /** Resets one failed item and creates its next index outbox fact. */
    int retryItem(long itemId, long jobId, long operatorId, long outboxId, String payload);

    /** A durable message eligible for publication. */
    record OutboxRow(long outboxId, long itemOrDocumentId, String topic, String payload) {}

    /** Authoritative document version state. */
    record DocumentView(long documentId, String version, boolean currentVersion) {}

    record IndexResult(
            long itemId,
            long documentId,
            String version,
            String status,
            int chunkCount,
            String errorCode,
            int attempt,
            long tokenCount,
            BigDecimal costAmount,
            String modelVersion,
            List<IndexChunk> chunks) {
        public IndexResult(
                long itemId,
                long documentId,
                String version,
                String status,
                int chunkCount,
                String errorCode,
                int attempt,
                long tokenCount,
                BigDecimal costAmount,
                String modelVersion) {
            this(
                    itemId,
                    documentId,
                    version,
                    status,
                    chunkCount,
                    errorCode,
                    attempt,
                    tokenCount,
                    costAmount,
                    modelVersion,
                    List.of());
        }

        public IndexResult {
            chunks = chunks == null ? List.of() : List.copyOf(chunks);
        }
    }

    /** Safe, bounded chunk facts returned by the Runtime index worker. */
    record IndexChunk(int chunkNo, String embeddingId, String sectionPath, String text) {}

    /** Batch-level progress projection. */
    record JobView(long jobId, String status, int totalItems, int indexedItems, int failedItems) {}

    /** File-level upload and indexing progress projection. */
    record ItemView(
            long itemId,
            long documentId,
            String filename,
            String uploadStatus,
            String indexStatus,
            int attempts,
            String errorCode) {}

    /** Resumable batch progress event. */
    record JobEvent(long eventId, String eventType, String payload) {}

    /** Sanitized import batch metadata owned by the application boundary. */
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

    /** Sanitized imported file metadata; file contents stay in object storage. */
    record ImportItem(
            long itemId,
            long jobId,
            long documentId,
            String filename,
            String contentType,
            long size) {}

    /** Minimal operation audit fact without business payload contents. */
    record Audit(
            long id,
            long operatorId,
            String traceId,
            String targetType,
            String targetId,
            String action) {}
}
