package com.foodmate.infrastructure.persistence.runtime.adapter;

import com.foodmate.application.runtime.port.out.AdmissionReconciliationRepository;
import com.foodmate.infrastructure.persistence.adapter.MapperRepositoryAdapter;
import com.foodmate.infrastructure.persistence.runtime.AdmissionReconciliationMapper;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("local")
public class AdmissionReconciliationRepositoryAdapter
        extends MapperRepositoryAdapter<AdmissionReconciliationRepository> {
    public AdmissionReconciliationRepositoryAdapter(AdmissionReconciliationMapper mapper) {
        super(mapper, AdmissionReconciliationRepository.class);
    }
}
