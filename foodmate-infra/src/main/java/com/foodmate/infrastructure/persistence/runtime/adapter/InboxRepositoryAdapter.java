package com.foodmate.infrastructure.persistence.runtime.adapter;

import com.foodmate.application.runtime.port.out.InboxRepository;
import com.foodmate.infrastructure.persistence.adapter.MapperRepositoryAdapter;
import com.foodmate.infrastructure.persistence.runtime.ProposalInboxMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("local")
public class InboxRepositoryAdapter extends MapperRepositoryAdapter<InboxRepository> {
    public InboxRepositoryAdapter(ProposalInboxMapper mapper) {
        super(mapper, InboxRepository.class);
    }
}
