package com.foodmate.infrastructure.persistence.account.adapter;

import com.foodmate.application.account.port.out.AdminManagementRepository;
import com.foodmate.infrastructure.persistence.account.AdminManagementMapper;
import com.foodmate.shared.account.enums.UserStatus;
import com.foodmate.shared.admin.enums.RestorableResourceType;
import com.foodmate.shared.runtime.enums.ToolStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnBean(AdminManagementMapper.class)
public class AdminManagementRepositoryAdapter implements AdminManagementRepository {
    private final AdminManagementMapper mapper;

    public AdminManagementRepositoryAdapter(AdminManagementMapper mapper) {
        this.mapper = mapper;
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
        return mapper.nextAuditId();
    }

    public void insertAudit(Audit audit) {
        mapper.insertAudit(audit);
    }
}
