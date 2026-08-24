package com.foodmate.application.knowledge.service;

import com.foodmate.application.knowledge.port.out.KnowledgeRepository;
import com.foodmate.shared.knowledge.enums.KnowledgeDocumentStatus;
import java.io.InputStream;
import java.util.List;

/** Knowledge document management use cases. */
public interface KnowledgeService {
    /** Accepts one legacy single-file upload and starts asynchronous indexing. */
    long upload(
            long operatorId,
            String filename,
            String contentType,
            long size,
            InputStream input,
            String traceId);

    /** Applies a document status transition for an authorized administrator. */
    void updateStatus(
            long documentId, KnowledgeDocumentStatus status, long operatorId, String traceId);

    /**
     * Creates one public-knowledge import batch; indexing and publication remain explicit later
     * steps.
     */
    long uploadBatch(long operatorId, ImportBatch batch, String traceId);

    /** Applies a public visibility transition and emits a replayable projection fact. */
    void changeVisibility(long documentId, String visibility, long operatorId, String traceId);

    /** Reads the current batch progress and its file items. */
    BatchDetail batch(long batchId);

    /** Reads batch progress events after the supplied SSE cursor. */
    List<BatchEvent> batchEvents(long batchId, long afterEventId);

    /** Requeues one failed item under an administrator action. */
    void retryItem(long batchId, long documentId, long operatorId, String traceId);

    record ImportBatch(
            String idempotencyKey,
            String sourceType,
            String sourceName,
            String sourceVersion,
            String licenseNotice,
            List<ImportFile> files) {}

    /** One sanitized upload part supplied to the batch use case. */
    record ImportFile(String filename, String contentType, long size, InputStream input) {}

    /** Batch progress view returned by the management API. */
    record BatchDetail(KnowledgeRepository.JobView job, List<KnowledgeRepository.ItemView> items) {}

    /** One resumable batch event returned by the management API. */
    record BatchEvent(long eventId, String eventType, String payload) {}
}
