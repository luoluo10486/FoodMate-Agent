package com.foodmate.infrastructure.persistence.audit;

import com.foodmate.application.common.port.out.OperationAuditPort;
import com.foodmate.application.common.port.out.OperationAuditPort.AuditRecord;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

/** 将统一审计端口连接到 PostgreSQL；业务模块不直接访问 operation_audits。 */
@Repository
@Profile("local")
public class OperationAuditPortAdapter implements OperationAuditPort {
    private final OperationAuditMapper mapper;

    public OperationAuditPortAdapter(OperationAuditMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public int insert(AuditRecord record) {
        return mapper.insert(record);
    }

    @Override
    public int reserve(AuditRecord record) {
        return mapper.reserve(record);
    }

    @Override
    public int complete(long operatorId, String idempotencyKey, String responseJson) {
        return mapper.complete(operatorId, idempotencyKey, responseJson);
    }

    @Override
    public int transition(
            long operatorId,
            String idempotencyKey,
            String result,
            String errorCode,
            String responseJson) {
        return mapper.transition(operatorId, idempotencyKey, result, errorCode, responseJson);
    }

    @Override
    public IdempotencyRecord findIdempotency(long operatorId, String idempotencyKey) {
        return mapper.findIdempotency(operatorId, idempotencyKey);
    }
}
