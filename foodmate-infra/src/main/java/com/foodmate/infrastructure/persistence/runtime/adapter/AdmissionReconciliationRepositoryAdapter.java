package com.foodmate.infrastructure.persistence.runtime.adapter;

import com.foodmate.application.runtime.port.out.AdmissionReconciliationRepository;
import com.foodmate.infrastructure.persistence.adapter.MapperRepositoryAdapter;
import com.foodmate.infrastructure.persistence.runtime.AdmissionReconciliationMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnBean(AdmissionReconciliationMapper.class)
public class AdmissionReconciliationRepositoryAdapter
        extends MapperRepositoryAdapter<AdmissionReconciliationRepository> {
    public AdmissionReconciliationRepositoryAdapter(AdmissionReconciliationMapper mapper) {
        super(mapper, AdmissionReconciliationRepository.class);
    }
}
