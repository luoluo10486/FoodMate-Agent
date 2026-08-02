package com.foodmate.infrastructure.persistence.runtime.adapter;

import com.foodmate.application.runtime.port.out.ProtocolAuditRepository;
import com.foodmate.infrastructure.persistence.adapter.MapperRepositoryAdapter;
import com.foodmate.infrastructure.persistence.runtime.ProtocolAuditMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnBean(ProtocolAuditMapper.class)
public class ProtocolAuditRepositoryAdapter
        extends MapperRepositoryAdapter<ProtocolAuditRepository> {
    public ProtocolAuditRepositoryAdapter(ProtocolAuditMapper mapper) {
        super(mapper, ProtocolAuditRepository.class);
    }
}
