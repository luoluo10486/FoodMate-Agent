package com.foodmate.infrastructure.persistence.account.adapter;

import com.foodmate.application.account.port.out.AdminAuditReportRepository;
import com.foodmate.infrastructure.persistence.account.AdminAuditReportMapper;
import com.foodmate.infrastructure.persistence.adapter.MapperRepositoryAdapter;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

/** 将运营审计聚合查询接入 application 端口。 */
@Repository
@Profile("local")
public class AdminAuditReportRepositoryAdapter
        extends MapperRepositoryAdapter<AdminAuditReportRepository> {
    public AdminAuditReportRepositoryAdapter(AdminAuditReportMapper mapper) {
        super(mapper, AdminAuditReportRepository.class);
    }
}
