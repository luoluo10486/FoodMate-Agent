package com.foodmate.application.knowledge.service.impl;

import com.foodmate.application.common.port.out.ObjectStoragePort;
import com.foodmate.application.knowledge.port.out.KnowledgeRepository;
import com.foodmate.application.knowledge.service.KnowledgeService;
import com.foodmate.shared.id.IdGenerator;
import com.foodmate.shared.knowledge.enums.KnowledgeDocumentStatus;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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

    @Transactional
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

    @Override
    @Transactional
    public long uploadBatch(long operatorId, ImportBatch batch, String traceId) {
        requireAvailable();
        validateBatch(batch);
        KnowledgeRepository.ImportJob existing =
                store.findImportJob(operatorId, batch.idempotencyKey());
        if (existing != null) {
            if (!sameBatch(existing, batch))
                throw new IllegalArgumentException("knowledge import idempotency key conflict");
            return existing.jobId();
        }
        long jobId = ids.nextId();
        String mode =
                System.getenv().getOrDefault("FOODMATE_RAG_MODE", "stub").toLowerCase(Locale.ROOT);
        if (!mode.equals("stub") && !mode.equals("local"))
            throw new IllegalArgumentException("invalid RAG mode");
        store.insertImportJob(
                new KnowledgeRepository.ImportJob(
                        jobId,
                        operatorId,
                        batch.idempotencyKey(),
                        mode,
                        batch.sourceType(),
                        batch.sourceName(),
                        batch.sourceVersion(),
                        batch.licenseNotice(),
                        traceId));
        List<String> uploadedKeys = new ArrayList<>();
        try {
            storage.ensureBucket(bucket);
            for (ImportFile file : batch.files()) {
                long documentId = ids.nextId();
                long itemId = ids.nextId();
                String filename = safeFilename(file.filename());
                String key = "knowledge/public/" + documentId + "/" + filename;
                storage.put(bucket, key, file.input(), file.size(), file.contentType());
                uploadedKeys.add(key);
                store.insertDocument(documentId, filename, key, operatorId);
                store.updateDocumentSource(
                        documentId,
                        batch.sourceType(),
                        batch.sourceName(),
                        batch.sourceVersion(),
                        batch.licenseNotice(),
                        operatorId);
                store.insertImportItem(
                        new KnowledgeRepository.ImportItem(
                                itemId,
                                jobId,
                                documentId,
                                filename,
                                file.contentType(),
                                file.size()));
                store.insertIndexOutbox(
                        ids.nextId(),
                        itemId,
                        "{\"job_id\":"
                                + jobId
                                + ",\"item_id\":"
                                + itemId
                                + ",\"document_id\":"
                                + documentId
                                + ",\"version\":\""
                                + jsonString(batch.sourceVersion())
                                + "\",\"mode\":\""
                                + mode
                                + "\"}");
            }
            audit(operatorId, traceId, "knowledge.import_batch.create", Long.toString(jobId));
            return jobId;
        } catch (Exception exception) {
            for (String key : uploadedKeys) {
                try {
                    storage.delete(bucket, key);
                } catch (Exception ignored) {
                    // Prefix reconciliation can remove an object when storage is temporarily down.
                }
            }
            throw new IllegalStateException("knowledge import batch failed", exception);
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

    @Override
    @Transactional
    public void changeVisibility(
            long documentId, String visibility, long operatorId, String traceId) {
        requireAvailable();
        if (!("published".equals(visibility)
                || "disabled".equals(visibility)
                || "deleted".equals(visibility)
                || "draft".equals(visibility)))
            throw new IllegalArgumentException("invalid knowledge visibility");
        if (store.updateVisibility(documentId, visibility, operatorId) != 1)
            throw new IllegalArgumentException(
                    "knowledge document is not eligible for visibility change");
        store.insertVisibilityOutbox(
                ids.nextId(),
                documentId,
                "{\"document_id\":"
                        + documentId
                        + ",\"visibility\":\""
                        + visibility
                        + "\",\"tenant_id\":0,\"scope\":\"public_published\"}");
        audit(operatorId, traceId, "knowledge.visibility." + visibility, Long.toString(documentId));
    }

    @Override
    public BatchDetail batch(long batchId) {
        requireAvailable();
        var job = store.job(batchId);
        if (job == null) throw new IllegalArgumentException("knowledge import batch not found");
        return new BatchDetail(job, store.jobItems(batchId));
    }

    @Override
    public java.util.List<KnowledgeService.BatchEvent> batchEvents(
            long batchId, long afterEventId) {
        batch(batchId);
        return store.jobEvents(batchId, Math.max(0, afterEventId)).stream()
                .map(
                        event ->
                                new KnowledgeService.BatchEvent(
                                        event.eventId(), event.eventType(), event.payload()))
                .toList();
    }

    @Override
    @Transactional
    public void retryItem(long batchId, long documentId, long operatorId, String traceId) {
        BatchDetail detail = batch(batchId);
        KnowledgeRepository.ItemView item =
                detail.items().stream()
                        .filter(value -> value.documentId() == documentId)
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new IllegalArgumentException(
                                                "knowledge document is not part of this batch"));
        if (store.retryItem(item.itemId(), batchId, operatorId, ids.nextId(), "{}") != 1)
            throw new IllegalArgumentException("knowledge import item is not retryable");
        audit(operatorId, traceId, "knowledge.import_item.retry", Long.toString(item.documentId()));
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

    private void validateBatch(ImportBatch batch) {
        if (batch == null
                || blank(batch.idempotencyKey())
                || blank(batch.sourceType())
                || blank(batch.sourceName())
                || blank(batch.sourceVersion())
                || blank(batch.licenseNotice())
                || batch.files() == null
                || batch.files().isEmpty()
                || batch.files().size() > 20)
            throw new IllegalArgumentException("invalid knowledge import batch");
        for (ImportFile file : batch.files()) {
            if (file == null
                    || file.input() == null
                    || file.size() <= 0
                    || file.size() > 20 * 1024 * 1024)
                throw new IllegalArgumentException("invalid knowledge import file");
            String name = safeFilename(file.filename()).toLowerCase(Locale.ROOT);
            if (!(name.endsWith(".pdf")
                    || name.endsWith(".docx")
                    || name.endsWith(".md")
                    || name.endsWith(".txt")))
                throw new IllegalArgumentException("unsupported knowledge document type");
        }
    }

    private String safeFilename(String filename) {
        if (blank(filename)
                || filename.contains("/")
                || filename.contains("\\")
                || filename.contains(".."))
            throw new IllegalArgumentException("invalid knowledge filename");
        return filename.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private boolean sameBatch(KnowledgeRepository.ImportJob existing, ImportBatch requested) {
        return existing.sourceType().equals(requested.sourceType())
                && existing.sourceName().equals(requested.sourceName())
                && existing.sourceVersion().equals(requested.sourceVersion())
                && existing.licenseNotice().equals(requested.licenseNotice());
    }

    private String jsonString(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
