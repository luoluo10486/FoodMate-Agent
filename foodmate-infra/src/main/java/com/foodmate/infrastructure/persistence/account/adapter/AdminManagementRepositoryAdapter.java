package com.foodmate.infrastructure.persistence.account.adapter;

import com.foodmate.application.account.port.out.AdminManagementRepository;
import com.foodmate.application.common.port.out.OperationAuditPort;
import com.foodmate.infrastructure.persistence.account.AdminManagementMapper;
import com.foodmate.shared.account.enums.UserStatus;
import com.foodmate.shared.admin.enums.RestorableResourceType;
import com.foodmate.shared.id.IdGenerator;
import com.foodmate.shared.runtime.enums.ToolStatus;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("local")
public class AdminManagementRepositoryAdapter implements AdminManagementRepository {
    private final AdminManagementMapper mapper;
    private final OperationAuditPort audit;
    private final IdGenerator ids;

    public AdminManagementRepositoryAdapter(
            AdminManagementMapper mapper, OperationAuditPort audit, IdGenerator ids) {
        this.mapper = mapper;
        this.audit = audit;
        this.ids = ids;
    }

    public int updateUserStatus(long userId, UserStatus status, long operatorId) {
        return mapper.updateUserStatus(userId, status.code(), operatorId);
    }

    public int revokeSessions(long userId, long operatorId) {
        return mapper.revokeSessions(userId, operatorId);
    }

    public int updateToolStatus(String name, ToolStatus status, long operatorId) {
        return mapper.updateToolStatus(name, status.code(), operatorId);
    }

    public int restore(RestorableResourceType resourceType, long resourceId, long operatorId) {
        return mapper.restore(resourceType.code(), resourceId, operatorId);
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
