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

    record ImportBatch(
            String idempotencyKey,
            String sourceType,
            String sourceName,
            String sourceVersion,
            String licenseNotice,
            List<ImportFile> files) {}

    record ImportFile(String filename, String contentType, long size, InputStream input) {}
}
