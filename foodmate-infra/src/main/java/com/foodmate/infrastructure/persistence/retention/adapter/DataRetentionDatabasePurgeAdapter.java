package com.foodmate.infrastructure.persistence.retention.adapter;

import com.foodmate.application.retention.port.out.DataRetentionDatabasePurgePort;
import com.foodmate.application.retention.port.out.DataRetentionDatabasePurgePort.PurgeResult;
import com.foodmate.infrastructure.persistence.retention.DataRetentionDatabasePurgeMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** 固定且幂等的数据库清理步骤的 PostgreSQL 实现。 */
@Repository
@Profile("local")
public class DataRetentionDatabasePurgeAdapter implements DataRetentionDatabasePurgePort {
    private final DataRetentionDatabasePurgeMapper mapper;

    public DataRetentionDatabasePurgeAdapter(DataRetentionDatabasePurgeMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    @Transactional
    public void purge(String resourceType, long resourceId) {
        purgeWithResult(resourceType, resourceId);
    }

    @Override
    @Transactional
    public PurgeResult purgeWithResult(String resourceType, long resourceId) {
        if (resourceId <= 0) throw new IllegalArgumentException("retention resource id is invalid");
        switch (resourceType) {
            case "knowledge_document" -> {
                return purgeKnowledgeDocument(resourceId);
            }
            case "admin_export_job" -> {
                return purgeAdminExportJob(resourceId);
            }
            default -> throw new IllegalArgumentException("retention resource type is invalid");
        }
    }

    private PurgeResult purgeKnowledgeDocument(long documentId) {
        requireAllowed(mapper.knowledgeDocumentPurgeAllowed(documentId));
        int deleted = 0;
        deleted += mapper.deleteKnowledgeIndexResults(documentId);
        deleted += mapper.deleteKnowledgeIndexOutbox(documentId);
        deleted += mapper.deleteKnowledgeImportEvents(documentId);
        deleted += mapper.deleteKnowledgeChunks(documentId);
        deleted += mapper.deleteKnowledgeVisibilityOutbox(documentId);
        deleted += mapper.deleteKnowledgeImportItems(documentId);
        deleted += mapper.deleteKnowledgeDocument(documentId);
        return new PurgeResult(
                "postgresql", deleted, mapper.knowledgeDocumentExists(documentId) != 1);
    }

    private PurgeResult purgeAdminExportJob(long jobId) {
        requireAllowed(mapper.adminExportJobPurgeAllowed(jobId));
        int deleted = mapper.deleteAdminExportJob(jobId);
        return new PurgeResult("postgresql", deleted, mapper.adminExportJobExists(jobId) != 1);
    }

    private void requireAllowed(int allowed) {
        if (allowed != 1)
            throw new IllegalStateException(
                    "retention database purge guard rejected the current resource state");
    }
}
