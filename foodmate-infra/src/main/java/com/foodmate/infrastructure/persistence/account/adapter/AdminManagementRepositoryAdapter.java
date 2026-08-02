package com.foodmate.infrastructure.persistence.account.adapter;

import com.foodmate.application.account.port.out.AdminManagementRepository;
import com.foodmate.infrastructure.persistence.account.AdminManagementMapper;
import com.foodmate.infrastructure.persistence.adapter.MapperRepositoryAdapter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnBean(AdminManagementMapper.class)
public class AdminManagementRepositoryAdapter
        extends MapperRepositoryAdapter<AdminManagementRepository> {
    public AdminManagementRepositoryAdapter(AdminManagementMapper mapper) {
        super(mapper, AdminManagementRepository.class);
    }
}
