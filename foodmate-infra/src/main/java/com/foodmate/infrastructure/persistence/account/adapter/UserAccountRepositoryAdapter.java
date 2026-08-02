package com.foodmate.infrastructure.persistence.account.adapter;

import com.foodmate.application.account.port.out.UserAccountRepository;
import com.foodmate.infrastructure.persistence.account.UserAccountMapper;
import com.foodmate.infrastructure.persistence.adapter.MapperRepositoryAdapter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnBean(UserAccountMapper.class)
public class UserAccountRepositoryAdapter extends MapperRepositoryAdapter<UserAccountRepository> {
    public UserAccountRepositoryAdapter(UserAccountMapper mapper) {
        super(mapper, UserAccountRepository.class);
    }
}
