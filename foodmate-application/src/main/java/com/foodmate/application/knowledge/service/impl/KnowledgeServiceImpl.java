package com.foodmate.application.knowledge.service.impl;

import com.foodmate.application.common.port.out.ObjectStoragePort;
import com.foodmate.application.knowledge.port.out.KnowledgeRepository;
import com.foodmate.application.knowledge.service.KnowledgeService;
import com.foodmate.shared.id.IdGenerator;
import com.foodmate.shared.knowledge.enums.KnowledgeDocumentStatus;
import java.io.InputStream;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KnowledgeServiceImpl implements KnowledgeService {
    private final KnowledgeRepository store;
    private final ObjectStoragePort storage;
    private final IdGenerator ids;
    private final String bucket;

    public KnowledgeServiceImpl(
            ObjectProvider<KnowledgeRepository> store,
            ObjectProvider<ObjectStoragePort> storage,
            ObjectProvider<IdGenerator> ids,
            @Value("${foodmate.storage.bucket:foodmate-private}") String bucket) {
        this.store = store.getIfAvailable();
        this.storage = storage.getIfAvailable();
        this.ids = ids.getIfAvailable();
        this.bucket = bucket;
    }

    public long upload(
            long operatorId,
            String filename,
            String contentType,
            long size,
            InputStream input,
            String traceId) {
        requireAvailable();
        long documentId = ids.nextId();
        String key =
                "knowledge/"
                        + operatorId
                        + "/"
                        + documentId
                        + "-"
                        + filename.replaceAll("[^A-Za-z0-9._-]", "_");
        try {
            storage.ensureBucket(bucket);
            storage.put(
                    bucket,
                    key,
                    input,
                    size,
                    contentType == null ? "application/octet-stream" : contentType);
            store.insertDocument(documentId, filename, key, operatorId);
            audit(operatorId, traceId, "knowledge.upload", Long.toString(documentId));
            return documentId;
        } catch (Exception exception) {
            throw new IllegalStateException("knowledge upload failed", exception);
        }
    }

    @Transactional
    public void updateStatus(
            long documentId, KnowledgeDocumentStatus status, long operatorId, String traceId) {
        requireAvailable();
        if (status == null) throw new IllegalArgumentException("invalid document status");
        if (store.updateStatus(documentId, status, operatorId) != 1)
            throw new IllegalArgumentException("document not found");
        audit(operatorId, traceId, "knowledge.status.update", Long.toString(documentId));
    }

    private void audit(long operatorId, String traceId, String action, String documentId) {
        store.insertAudit(
                new KnowledgeRepository.Audit(
                        store.nextAuditId(),
                        operatorId,
                        traceId,
                        "knowledge_document",
                        documentId,
                        action));
    }

    private void requireAvailable() {
        if (store == null || storage == null || ids == null)
            throw new IllegalStateException("knowledge dependencies unavailable");
    }
}
