package com.foodmate.infrastructure.persistence.runtime.adapter;

import com.foodmate.application.runtime.port.out.ToolGatewayPort;
import com.foodmate.infrastructure.persistence.adapter.MapperRepositoryAdapter;
import com.foodmate.infrastructure.persistence.runtime.ToolGatewayMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnBean(ToolGatewayMapper.class)
public class ToolGatewayPortAdapter extends MapperRepositoryAdapter<ToolGatewayPort> {
    public ToolGatewayPortAdapter(ToolGatewayMapper mapper) {
        super(mapper, ToolGatewayPort.class);
    }
}
