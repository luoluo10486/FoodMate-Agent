package com.foodmate.application.retention.service.impl;

import com.foodmate.application.retention.port.out.DataRetentionRepository;
import com.foodmate.application.retention.port.out.DataRetentionRepository.PurgeTaskContext;
import com.foodmate.application.retention.port.out.DataRetentionRepository.PurgeTaskResult;
import com.foodmate.application.retention.service.DataRetentionDeliveryService;
import com.foodmate.shared.id.IdGenerator;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 协调数据保留清理任务的投递和持久化结果收敛。 */
@Service
public class DataRetentionDeliveryServiceImpl implements DataRetentionDeliveryService {
    private final DataRetentionRepository store;
    private final IdGenerator ids;

    /** 兼容仅测试应用层且不执行结果台账的构造方法。 */
    public DataRetentionDeliveryServiceImpl(DataRetentionRepository store) {
        this(store, () -> 0L);
    }

    @Autowired
    public DataRetentionDeliveryServiceImpl(DataRetentionRepository store, IdGenerator ids) {
        this.store = store;
        this.ids = Objects.requireNonNull(ids);
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
    public void succeeded(PurgeExecution execution) {
        if (execution == null
                || execution.taskId() <= 0
                || execution.owner() == null
                || execution.owner().isBlank()
                || execution.backend() == null
                || execution.backend().isBlank()
                || execution.deletedCount() < 0
                || !execution.verifiedAbsent()) {
            throw new IllegalArgumentException("retention execution result is invalid");
        }
        PurgeTaskContext context = requireContext(execution.taskId());
        String version = normalizeVersion(execution.version());
        if (!versionMatches(context, version))
            throw new IllegalArgumentException("retention execution version conflicts with task");
        PurgeTaskResult result =
                result(
                        context,
                        version,
                        "succeeded",
                        execution.backend(),
                        execution.deletedCount(),
                        true,
                        null,
                        "",
                        "");
        store.insertPurgeTaskResult(result);
        if (store.markTaskSucceeded(execution.taskId(), execution.owner(), "", "") == 1)
            refresh(execution.taskId());
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

    @Override
    @Transactional
    public void acceptResult(ExternalResult external) {
        if (external == null
                || external.taskId() <= 0
                || external.requestId() <= 0
                || external.resourceId() <= 0
                || external.status() == null
                || !("succeeded".equals(external.status()) || "failed".equals(external.status()))
                || external.backend() == null
                || external.backend().isBlank()
                || external.deletedCount() < 0
                || ("succeeded".equals(external.status()) && !external.verifiedAbsent())) {
            throw new IllegalArgumentException("retention external result is invalid");
        }
        PurgeTaskContext context = requireContext(external.taskId());
        if (context.requestId() != external.requestId()
                || !context.resourceType().equals(external.resourceType())
                || context.resourceId() != external.resourceId()
                || !context.taskType().equals(external.taskType())
                || !versionMatches(context, normalizeVersion(external.version()))) {
            throw new IllegalArgumentException("retention external result conflicts with task");
        }
        String version = normalizeVersion(external.version());
        PurgeTaskResult result =
                result(
                        context,
                        version,
                        external.status(),
                        external.backend(),
                        external.deletedCount(),
                        external.verifiedAbsent(),
                        external.messageId(),
                        external.errorCode(),
                        external.errorSummary());
        store.insertPurgeTaskResult(result);
        if (store.applyTaskResult(
                        external.taskId(),
                        external.status(),
                        external.errorCode(),
                        external.errorSummary())
                == 1) refresh(external.taskId());
    }

    private PurgeTaskContext requireContext(long taskId) {
        PurgeTaskContext context = store.purgeTaskContext(taskId);
        if (context == null) throw new IllegalArgumentException("retention task does not exist");
        return context;
    }

    private boolean versionMatches(PurgeTaskContext context, String version) {
        return context.version() == null
                || context.version().isBlank()
                || context.version().equals(version);
    }

    private String normalizeVersion(String version) {
        return version == null ? "" : version.trim();
    }

    private PurgeTaskResult result(
            PurgeTaskContext context,
            String version,
            String status,
            String backend,
            int deletedCount,
            boolean verifiedAbsent,
            String messageId,
            String errorCode,
            String errorSummary) {
        String safeBackend = backend.trim();
        String safeMessageId = messageId == null ? "" : messageId.trim();
        String safeErrorCode = bound(errorCode, 64);
        String safeSummary = bound(errorSummary);
        return new PurgeTaskResult(
                ids.nextId(),
                context.taskId(),
                context.requestId(),
                context.resourceType(),
                context.resourceId(),
                context.taskType(),
                version,
                status,
                safeBackend,
                deletedCount,
                verifiedAbsent,
                safeMessageId,
                digest(
                        context.taskId(),
                        context.requestId(),
                        context.resourceType(),
                        context.resourceId(),
                        context.taskType(),
                        version,
                        status,
                        safeBackend,
                        deletedCount,
                        verifiedAbsent,
                        safeMessageId,
                        safeErrorCode,
                        safeSummary),
                safeErrorCode,
                safeSummary);
    }

    private String bound(String value) {
        return bound(value, 512);
    }

    private String bound(String value, int limit) {
        if (value == null) return "";
        return value.substring(0, Math.min(limit, value.length()));
    }

    private String digest(Object... values) {
        String canonical =
                java.util.Arrays.stream(values)
                        .map(String::valueOf)
                        .reduce((a, b) -> a + "|" + b)
                        .orElse("");
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private void refresh(long taskId) {
        // The repository resolves the request ID from the task, keeping request convergence atomic.
        store.refreshPurgeRequest(taskId);
    }
}
