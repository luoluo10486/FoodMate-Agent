package com.foodmate.infrastructure.persistence.runtime.adapter;

import com.foodmate.application.runtime.port.out.CancellationRepository;
import com.foodmate.infrastructure.persistence.adapter.MapperRepositoryAdapter;
import com.foodmate.infrastructure.persistence.runtime.CancellationMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("local")
public class CancellationRepositoryAdapter extends MapperRepositoryAdapter<CancellationRepository> {
    public CancellationRepositoryAdapter(CancellationMapper mapper) {
        super(mapper, CancellationRepository.class);
    }
}
