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
}
