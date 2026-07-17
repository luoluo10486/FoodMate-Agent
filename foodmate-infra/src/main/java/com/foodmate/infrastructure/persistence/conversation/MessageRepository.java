package com.foodmate.infrastructure.persistence.conversation;

import com.foodmate.infrastructure.persistence.SoftDeleteRepositorySupport;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnBean(MessageMapper.class)
public class MessageRepository extends SoftDeleteRepositorySupport<MessagePo> {
    public MessageRepository(MessageMapper mapper) { super(mapper); }
    public MessagePo findById(Long messageId) { return mapper().selectById(messageId); }
}
