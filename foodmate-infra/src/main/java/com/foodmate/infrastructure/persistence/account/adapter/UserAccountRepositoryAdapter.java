package com.foodmate.infrastructure.persistence.account.adapter;

import com.foodmate.application.account.port.out.UserAccountRepository;
import com.foodmate.infrastructure.persistence.account.UserAccountMapper;
import com.foodmate.infrastructure.persistence.adapter.MapperRepositoryAdapter;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("local")
public class UserAccountRepositoryAdapter extends MapperRepositoryAdapter<UserAccountRepository> {
    public UserAccountRepositoryAdapter(UserAccountMapper mapper) {
        super(mapper, UserAccountRepository.class);
    }
}
