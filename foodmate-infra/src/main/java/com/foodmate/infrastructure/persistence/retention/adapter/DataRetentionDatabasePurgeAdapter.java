package com.foodmate.infrastructure.persistence.retention.adapter;

import com.foodmate.application.retention.port.out.DataRetentionDatabasePurgePort;
import com.foodmate.infrastructure.persistence.retention.DataRetentionDatabasePurgeMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** PostgreSQL implementation of the fixed, idempotent database purge steps. */
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
        if (resourceId <= 0) throw new IllegalArgumentException("retention resource id is invalid");
        switch (resourceType) {
            case "knowledge_document" -> purgeKnowledgeDocument(resourceId);
            case "admin_export_job" -> mapper.deleteAdminExportJob(resourceId);
            default -> throw new IllegalArgumentException("retention resource type is invalid");
        }
    }

    private void purgeKnowledgeDocument(long documentId) {
        mapper.deleteKnowledgeIndexResults(documentId);
        mapper.deleteKnowledgeIndexOutbox(documentId);
        mapper.deleteKnowledgeImportEvents(documentId);
        mapper.deleteKnowledgeChunks(documentId);
        mapper.deleteKnowledgeVisibilityOutbox(documentId);
        mapper.deleteKnowledgeImportItems(documentId);
        mapper.deleteKnowledgeDocument(documentId);
    }
}
