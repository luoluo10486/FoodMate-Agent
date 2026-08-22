package com.foodmate.infrastructure.persistence.account.adapter;

import com.foodmate.application.account.port.out.AdminOperationalQueryRepository;
import com.foodmate.infrastructure.persistence.account.AdminOperationalQueryMapper;
import com.foodmate.infrastructure.persistence.adapter.MapperRepositoryAdapter;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("local")
public class AdminOperationalQueryRepositoryAdapter
        extends MapperRepositoryAdapter<AdminOperationalQueryRepository> {
    public AdminOperationalQueryRepositoryAdapter(AdminOperationalQueryMapper mapper) {
        super(mapper, AdminOperationalQueryRepository.class);
    }
}
