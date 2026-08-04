package com.foodmate.infrastructure.persistence.knowledge.adapter;

import com.foodmate.application.knowledge.port.out.KnowledgeRepository;
import com.foodmate.infrastructure.persistence.knowledge.KnowledgeMapper;
import com.foodmate.shared.knowledge.enums.KnowledgeDocumentStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnBean(KnowledgeMapper.class)
public class KnowledgeRepositoryAdapter implements KnowledgeRepository {
    private final KnowledgeMapper mapper;

    public KnowledgeRepositoryAdapter(KnowledgeMapper mapper) {
        this.mapper = mapper;
    }

    public void insertDocument(long documentId, String title, String storageKey, long operatorId) {
        mapper.insertDocument(documentId, title, storageKey, operatorId);
    }

    public int updateStatus(long documentId, KnowledgeDocumentStatus status, long operatorId) {
        return mapper.updateStatus(documentId, status.code(), operatorId);
    }

    public long nextAuditId() {
        return mapper.nextAuditId();
    }

    public void insertAudit(Audit audit) {
        mapper.insertAudit(audit);
    }
}
