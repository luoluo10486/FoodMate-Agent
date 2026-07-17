package com.foodmate.infrastructure.persistence.conversation;

import com.foodmate.infrastructure.persistence.SoftDeleteRepositorySupport;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnBean(SessionMapper.class)
public class SessionRepository extends SoftDeleteRepositorySupport<SessionPo> {
    public SessionRepository(SessionMapper mapper) { super(mapper); }
    public SessionPo findById(Long sessionId) { return mapper().selectById(sessionId); }
}
