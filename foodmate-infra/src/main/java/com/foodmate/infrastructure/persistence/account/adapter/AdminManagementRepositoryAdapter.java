package com.foodmate.infrastructure.persistence.account.adapter;

import com.foodmate.application.account.port.out.AdminManagementRepository;
import com.foodmate.application.account.port.out.AdminManagementRepository.RevokeResult;
import com.foodmate.infrastructure.persistence.account.AdminManagementMapper;
import com.foodmate.shared.account.enums.UserStatus;
import com.foodmate.shared.admin.enums.RestorableResourceType;
import com.foodmate.shared.runtime.enums.ToolStatus;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

/** PostgreSQL 管理写操作适配器；业务审计由 application 统一端口提交。 */
@Repository
@Profile("local")
public class AdminManagementRepositoryAdapter implements AdminManagementRepository {
    private final AdminManagementMapper mapper;

    public AdminManagementRepositoryAdapter(AdminManagementMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public UserSnapshot findUser(long userId) {
        return mapper.findUser(userId);
    }

    @Override
    public ToolSnapshot findTool(String name) {
        return mapper.findTool(name);
    }

    @Override
    public ResourceSnapshot findResource(RestorableResourceType resourceType, long resourceId) {
        return mapper.findResource(resourceType.code(), resourceId);
    }

    @Override
    public int updateUserStatus(long userId, UserStatus status, long operatorId, long revision) {
        return mapper.updateUserStatus(userId, status.code(), operatorId, revision);
    }

    @Override
    public RevokeResult revokeSessions(long userId, long operatorId, long revision) {
        if (mapper.bumpUserRevision(userId, operatorId, revision) != 1) return null;
        return new RevokeResult(mapper.revokeSessions(userId, operatorId), revision + 1);
    }

    @Override
    public int updateToolStatus(String name, ToolStatus status, long operatorId, long revision) {
        return mapper.updateToolStatus(name, status.code(), operatorId, revision);
    }

    @Override
    public int restore(
            RestorableResourceType resourceType, long resourceId, long operatorId, long revision) {
        return mapper.restore(resourceType.code(), resourceId, operatorId, revision);
    }
}
