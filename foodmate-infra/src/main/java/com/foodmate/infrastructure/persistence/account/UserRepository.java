package com.foodmate.infrastructure.persistence.account;

import com.foodmate.infrastructure.persistence.SoftDeleteRepositorySupport;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnBean(UserMapper.class)
public class UserRepository extends SoftDeleteRepositorySupport<UserPo> {
    public UserRepository(UserMapper mapper) { super(mapper); }
    public UserPo findById(Long userId) { return mapper().selectById(userId); }
}
