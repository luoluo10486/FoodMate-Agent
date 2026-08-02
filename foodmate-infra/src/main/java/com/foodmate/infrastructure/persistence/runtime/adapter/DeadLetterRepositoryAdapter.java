package com.foodmate.infrastructure.persistence.runtime.adapter;

import com.foodmate.application.runtime.port.out.DeadLetterRepository;
import com.foodmate.infrastructure.persistence.adapter.MapperRepositoryAdapter;
import com.foodmate.infrastructure.persistence.runtime.DlqMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnBean(DlqMapper.class)
public class DeadLetterRepositoryAdapter extends MapperRepositoryAdapter<DeadLetterRepository> {
    public DeadLetterRepositoryAdapter(DlqMapper mapper) {
        super(mapper, DeadLetterRepository.class);
    }
}
