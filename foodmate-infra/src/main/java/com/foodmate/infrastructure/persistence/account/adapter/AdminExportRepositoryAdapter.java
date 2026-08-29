package com.foodmate.infrastructure.persistence.account.adapter;

import com.foodmate.application.account.port.out.AdminExportRepository;
import com.foodmate.infrastructure.persistence.account.AdminExportMapper;
import com.foodmate.infrastructure.persistence.adapter.MapperRepositoryAdapter;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

/** 管理员导出任务的 PostgreSQL 适配器。 */
@Repository
@Profile("local")
public class AdminExportRepositoryAdapter extends MapperRepositoryAdapter<AdminExportRepository> {
    public AdminExportRepositoryAdapter(AdminExportMapper mapper) {
        super(mapper, AdminExportRepository.class);
    }
}
