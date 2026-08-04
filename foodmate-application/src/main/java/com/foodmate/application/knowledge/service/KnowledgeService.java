package com.foodmate.application.knowledge.service;

import com.foodmate.shared.knowledge.enums.KnowledgeDocumentStatus;
import java.io.InputStream;

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
}
