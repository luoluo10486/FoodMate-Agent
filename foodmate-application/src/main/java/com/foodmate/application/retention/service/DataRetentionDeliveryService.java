package com.foodmate.application.retention.service;

import com.foodmate.application.retention.port.out.DataRetentionRepository;
import java.util.List;

/** 编排已批准的保留任务及幂等处理 Worker 结果。 */
public interface DataRetentionDeliveryService {
    List<DataRetentionRepository.PurgeTaskSnapshot> pending(int limit);

    int lease(long taskId, String owner, String resourceType, long resourceId);

    void published(long taskId, String owner, String messageId);

    void succeeded(long taskId, String owner, String errorCode, String errorSummary);

    /** 记录已验证的本地清理结果，然后完成已租约任务。 */
    void succeeded(PurgeExecution execution);

    void retry(long taskId, String owner, String errorCode, String errorSummary);

    void acceptResult(long taskId, String status, String errorCode, String errorSummary);

    /** 在任务收敛前校验并记录外部清理结果。 */
    void acceptResult(ExternalResult result);

    record PurgeExecution(
            long taskId,
            String owner,
            String version,
            String backend,
            int deletedCount,
            boolean verifiedAbsent) {}

    record ExternalResult(
            long taskId,
            long requestId,
            String resourceType,
            long resourceId,
            String taskType,
            String version,
            String status,
            String backend,
            int deletedCount,
            boolean verifiedAbsent,
            String messageId,
            String errorCode,
            String errorSummary) {}
}
