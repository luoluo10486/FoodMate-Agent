package com.foodmate.application.common.port.out;

/** 持久化用户或管理员业务命令的统一审计事实。 */
public interface OperationAuditPort {
    /** 写入一条已完成或已拒绝的审计事实。 */
    int insert(AuditRecord record);

    /** 以 pending 状态占用幂等键，供带幂等键的业务写入使用。 */
    int reserve(AuditRecord record);

    /** 将 pending 审计事实收敛为 success，并保存安全结果摘要。 */
    int complete(long operatorId, String idempotencyKey, String responseJson);

    /** 将 pending 审计事实收敛为失败或其他终态。 */
    int transition(
            long operatorId,
            String idempotencyKey,
            String result,
            String errorCode,
            String responseJson);

    /** 查询同一操作者的已保留审计事实，用于业务写操作的幂等重放。 */
    IdempotencyRecord findIdempotency(long operatorId, String idempotencyKey);

    /** 由统一审计适配器持久化的安全操作字段。 */
    record AuditRecord(
            long operationAuditId,
            Long operatorId,
            String requestId,
            String traceId,
            String targetType,
            String targetId,
            String action,
            String result,
            String errorCode,
            String requestJson,
            String responseJson,
            String parametersDigest,
            String idempotencyKey) {}

    /** 为幂等重放返回的已有审计事实。 */
    record IdempotencyRecord(String parametersDigest, String result, String responseJson) {}
}
