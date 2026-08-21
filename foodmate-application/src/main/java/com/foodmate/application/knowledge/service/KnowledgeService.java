package com.foodmate.application.knowledge.service;

import com.foodmate.shared.knowledge.enums.KnowledgeDocumentStatus;
import java.io.InputStream;
import java.util.List;

/** Knowledge document management use cases. */
public interface KnowledgeService {
    long upload(
            long operatorId,
            String filename,
            String contentType,
            long size,
            InputStream input,
            String traceId);

    void updateStatus(
            long documentId, KnowledgeDocumentStatus status, long operatorId, String traceId);

    /**
     * Creates one public-knowledge import batch; indexing and publication remain explicit later
     * steps.
     */
    long uploadBatch(long operatorId, ImportBatch batch, String traceId);

    void changeVisibility(long documentId, String visibility, long operatorId, String traceId);

    BatchDetail batch(long batchId);

    java.util.List<BatchEvent> batchEvents(long batchId, long afterEventId);

    void retryItem(long batchId, long itemId, long operatorId, String traceId);

    record ImportBatch(
            String idempotencyKey,
            String sourceType,
            String sourceName,
            String sourceVersion,
            String licenseNotice,
            List<ImportFile> files) {}

    record ImportFile(String filename, String contentType, long size, InputStream input) {}

    record BatchDetail(
            com.foodmate.application.knowledge.port.out.KnowledgeRepository.JobView job,
            java.util.List<com.foodmate.application.knowledge.port.out.KnowledgeRepository.ItemView>
                    items) {}

    record BatchEvent(long eventId, String eventType, String payload) {}
}
