package com.foodmate.infrastructure.persistence.conversation.adapter;

import com.foodmate.application.conversation.port.out.ConversationSummaryRepository;
import com.foodmate.infrastructure.persistence.adapter.MapperRepositoryAdapter;
import com.foodmate.infrastructure.persistence.conversation.SessionSummaryMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnBean(SessionSummaryMapper.class)
public class ConversationSummaryRepositoryAdapter
        extends MapperRepositoryAdapter<ConversationSummaryRepository> {
    public ConversationSummaryRepositoryAdapter(SessionSummaryMapper mapper) {
        super(mapper, ConversationSummaryRepository.class);
    }
}
