package com.foodmate.infrastructure.persistence.account.adapter;

import com.foodmate.application.account.port.out.AdminDashboardRepository;
import com.foodmate.infrastructure.persistence.account.AdminDashboardMapper;
import com.foodmate.infrastructure.persistence.adapter.MapperRepositoryAdapter;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("local")
public class AdminDashboardRepositoryAdapter
        extends MapperRepositoryAdapter<AdminDashboardRepository> {
    public AdminDashboardRepositoryAdapter(AdminDashboardMapper mapper) {
        super(mapper, AdminDashboardRepository.class);
    }
}
