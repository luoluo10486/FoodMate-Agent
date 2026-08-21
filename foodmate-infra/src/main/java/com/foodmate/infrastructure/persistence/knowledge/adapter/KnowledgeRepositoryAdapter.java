package com.foodmate.infrastructure.persistence.knowledge.adapter;

import com.foodmate.application.common.port.out.OperationAuditPort;
import com.foodmate.application.knowledge.port.out.KnowledgeRepository;
import com.foodmate.infrastructure.persistence.knowledge.KnowledgeMapper;
import com.foodmate.shared.id.IdGenerator;
import com.foodmate.shared.knowledge.enums.KnowledgeDocumentStatus;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("local")
public class KnowledgeRepositoryAdapter implements KnowledgeRepository {
    private final KnowledgeMapper mapper;
    private final OperationAuditPort audit;
    private final IdGenerator ids;

    public KnowledgeRepositoryAdapter(
            KnowledgeMapper mapper, OperationAuditPort audit, IdGenerator ids) {
        this.mapper = mapper;
        this.audit = audit;
        this.ids = ids;
    }

    public void insertDocument(long documentId, String title, String storageKey, long operatorId) {
        mapper.insertDocument(documentId, title, storageKey, operatorId);
    }

    @Override
    public void updateDocumentSource(
            long documentId,
            String sourceType,
            String sourceName,
            String sourceVersion,
            String licenseNotice,
            long operatorId) {
        mapper.updateDocumentSource(
                documentId, sourceType, sourceName, sourceVersion, licenseNotice, operatorId);
    }

    public int updateStatus(long documentId, KnowledgeDocumentStatus status, long operatorId) {
        return mapper.updateStatus(documentId, status.code(), operatorId);
    }

    public long nextAuditId() {
        return ids.nextId();
    }

    public void insertAudit(Audit audit) {
        int inserted =
                this.audit.insert(
                        new OperationAuditPort.AuditRecord(
                                audit.id(),
                                audit.operatorId(),
                                null,
                                audit.traceId(),
                                audit.targetType(),
                                audit.targetId(),
                                audit.action(),
                                "success",
                                null,
                                "{}",
                                "{}",
                                null,
                                null));
        if (inserted != 1) throw new IllegalStateException("operation audit was not persisted");
    }

    @Override
    public void insertImportJob(ImportJob job) {
        mapper.insertImportJob(
                job.jobId(),
                job.operatorId(),
                job.idempotencyKey(),
                job.mode(),
                job.sourceType(),
                job.sourceName(),
                job.sourceVersion(),
                job.licenseNotice(),
                job.traceId());
    }

    @Override
    public void insertImportItem(ImportItem item) {
        mapper.insertImportItem(
                item.itemId(),
                item.jobId(),
                item.documentId(),
                item.filename(),
                item.contentType(),
                item.size());
    }

    @Override
    public void insertIndexOutbox(long outboxId, long itemId, String payload) {
        mapper.insertIndexOutbox(outboxId, itemId, payload);
    }

    @Override
    public int updateVisibility(long documentId, String visibility, long operatorId) {
        return mapper.updateVisibility(documentId, visibility, operatorId);
    }

    @Override
    public void insertVisibilityOutbox(long outboxId, long documentId, String payload) {
        mapper.insertVisibilityOutbox(outboxId, documentId, payload);
    }

    @Override
    public java.util.List<OutboxRow> pendingIndexOutbox(int limit) {
        return mapper.pendingIndexOutbox(limit);
    }

    @Override
    public java.util.List<OutboxRow> pendingVisibilityOutbox(int limit) {
        return mapper.pendingVisibilityOutbox(limit);
    }

    @Override
    public int leaseIndexOutbox(long id, String owner) {
        return mapper.leaseIndexOutbox(id, owner);
    }

    @Override
    public int leaseVisibilityOutbox(long id, String owner) {
        return mapper.leaseVisibilityOutbox(id, owner);
    }

    @Override
    public void markIndexOutboxPublished(long id) {
        mapper.markIndexOutboxPublished(id);
    }

    @Override
    public void markVisibilityOutboxPublished(long id) {
        mapper.markVisibilityOutboxPublished(id);
    }

    @Override
    public void retryIndexOutbox(long id, String error) {
        mapper.retryIndexOutbox(id, error);
    }

    @Override
    public void retryVisibilityOutbox(long id, String error) {
        mapper.retryVisibilityOutbox(id, error);
    }

    @Override
    public void applyIndexResult(IndexResult result, String hash) {
        if (mapper.insertResultInbox(result.itemId(), result.version(), result.attempt(), hash)
                == 0) return;
        if ("indexed".equals(result.status())) {
            mapper.markItemIndexed(
                    result.itemId(), result.documentId(), result.attempt(), result.chunkCount());
            mapper.markDocumentIndexed(result.documentId());
        } else {
            int attempt = Math.max(1, result.attempt());
            mapper.markItemFailed(
                    result.itemId(), result.documentId(), result.errorCode(), attempt);
            if (attempt < 3)
                mapper.requeueIndexOutbox(
                        result.itemId(), attempt + 1, 1 << (attempt - 1), result.errorCode());
        }
        mapper.refreshJob(result.itemId());
        mapper.insertJobEvent(
                ids.nextId(),
                result.itemId(),
                "knowledge.index." + result.status(),
                "{\"item_id\":"
                        + result.itemId()
                        + ",\"document_id\":"
                        + result.documentId()
                        + ",\"status\":\""
                        + result.status()
                        + "\"}");
    }

    @Override
    public JobView job(long jobId) {
        return mapper.job(jobId);
    }

    @Override
    public java.util.List<ItemView> jobItems(long jobId) {
        return mapper.jobItems(jobId);
    }

    @Override
    public java.util.List<JobEvent> jobEvents(long jobId, long after) {
        return mapper.jobEvents(jobId, after);
    }

    @Override
    public int retryItem(long itemId, long operatorId, long outboxId, String payload) {
        int changed = mapper.resetItem(itemId);
        if (changed == 1) {
            mapper.deleteResultInbox(itemId);
            mapper.requeueIndexOutbox(itemId, 1, 0, null);
        }
        return changed;
    }
}
