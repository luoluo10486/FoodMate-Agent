package com.foodmate.application.retention.service;

import com.foodmate.application.retention.port.out.DataRetentionRepository;
import java.util.List;

/** Application orchestration for approved retention tasks and idempotent worker results. */
public interface DataRetentionDeliveryService {
    List<DataRetentionRepository.PurgeTaskSnapshot> pending(int limit);

    int lease(long taskId, String owner, String resourceType, long resourceId);

    void published(long taskId, String owner, String messageId);

    void succeeded(long taskId, String owner, String errorCode, String errorSummary);

    void retry(long taskId, String owner, String errorCode, String errorSummary);

    void acceptResult(long taskId, String status, String errorCode, String errorSummary);
}
