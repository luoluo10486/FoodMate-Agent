package com.foodmate.application.retention.service.impl;

import com.foodmate.application.retention.port.out.DataRetentionRepository;
import com.foodmate.application.retention.service.DataRetentionDeliveryService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Coordinates delivery and durable result convergence for data-retention purge tasks. */
@Service
public class DataRetentionDeliveryServiceImpl implements DataRetentionDeliveryService {
    private final DataRetentionRepository store;

    public DataRetentionDeliveryServiceImpl(DataRetentionRepository store) {
        this.store = store;
    }

    @Override
    public List<DataRetentionRepository.PurgeTaskSnapshot> pending(int limit) {
        return store.pendingTasks(limit);
    }

    @Override
    public int lease(long taskId, String owner, String resourceType, long resourceId) {
        return store.leaseTask(taskId, owner, resourceType, resourceId);
    }

    @Override
    public void published(long taskId, String owner, String messageId) {
        store.markTaskPublished(taskId, owner, messageId);
    }

    @Override
    @Transactional
    public void succeeded(long taskId, String owner, String errorCode, String errorSummary) {
        if (store.markTaskSucceeded(taskId, owner, errorCode, errorSummary) == 1) refresh(taskId);
    }

    @Override
    @Transactional
    public void retry(long taskId, String owner, String errorCode, String errorSummary) {
        store.retryTask(taskId, owner, errorCode, errorSummary);
        // A terminal task failure must be reflected on the parent request immediately.
        store.refreshPurgeRequest(taskId);
    }

    @Override
    @Transactional
    public void acceptResult(long taskId, String status, String errorCode, String errorSummary) {
        if (store.applyTaskResult(taskId, status, errorCode, errorSummary) == 1) refresh(taskId);
    }

    private void refresh(long taskId) {
        // The repository resolves the request ID from the task, keeping request convergence atomic.
        store.refreshPurgeRequest(taskId);
    }
}
