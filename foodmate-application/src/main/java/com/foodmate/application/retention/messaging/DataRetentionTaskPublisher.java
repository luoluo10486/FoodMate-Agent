package com.foodmate.application.retention.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodmate.application.common.port.out.ObjectStoragePort;
import com.foodmate.application.retention.port.out.DataRetentionDatabasePurgePort;
import com.foodmate.application.retention.port.out.DataRetentionRepository.PurgeTaskSnapshot;
import com.foodmate.application.retention.service.DataRetentionDeliveryService;
import com.foodmate.application.runtime.messaging.MessageProperties;
import com.foodmate.application.runtime.port.out.MessagePublisherPort;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 仅在显式开启本地开关后执行已批准的外部清理步骤。 */
@Component
public class DataRetentionTaskPublisher {
    private final DataRetentionDeliveryService service;
    private final ObjectStoragePort storage;
    private final MessagePublisherPort publisher;
    private final DataRetentionDatabasePurgePort databasePurge;
    private final ObjectMapper mapper;
    private final boolean enabled;
    private final boolean backupVerified;
    private final boolean rocketMq;
    private final String bucket;

    public DataRetentionTaskPublisher(
            DataRetentionDeliveryService service,
            ObjectProvider<ObjectStoragePort> storage,
            ObjectProvider<MessagePublisherPort> publisher,
            @Value("${foodmate.retention.execution.enabled:false}") boolean enabled,
            @Value("${foodmate.runtime.transport:http}") String transport,
            @Value("${foodmate.storage.bucket:foodmate-private}") String bucket) {
        this(
                service,
                storage,
                publisher,
                (DataRetentionDatabasePurgePort) null,
                enabled,
                transport,
                bucket,
                false);
    }

    /** 供直接提供数据库清理端口的业务测试使用的构造方法。 */
    public DataRetentionTaskPublisher(
            DataRetentionDeliveryService service,
            ObjectProvider<ObjectStoragePort> storage,
            ObjectProvider<MessagePublisherPort> publisher,
            DataRetentionDatabasePurgePort databasePurge,
            boolean enabled,
            String transport,
            String bucket) {
        this(service, storage, publisher, databasePurge, enabled, transport, bucket, false);
    }

    /** 供不需要数据库清理端口的显式备份校验测试使用的构造方法。 */
    public DataRetentionTaskPublisher(
            DataRetentionDeliveryService service,
            ObjectProvider<ObjectStoragePort> storage,
            ObjectProvider<MessagePublisherPort> publisher,
            boolean enabled,
            String transport,
            String bucket,
            boolean backupVerified) {
        this(
                service,
                storage,
                publisher,
                (DataRetentionDatabasePurgePort) null,
                enabled,
                transport,
                bucket,
                backupVerified);
    }

    /**
     * 构造清理执行器；硬删除必须同时具备显式备份校验事实。
     *
     * @param backupVerified 是否已由受控备份流程验证可恢复
     */
    public DataRetentionTaskPublisher(
            DataRetentionDeliveryService service,
            ObjectProvider<ObjectStoragePort> storage,
            ObjectProvider<MessagePublisherPort> publisher,
            DataRetentionDatabasePurgePort databasePurge,
            boolean enabled,
            String transport,
            String bucket,
            boolean backupVerified) {
        this.service = service;
        this.storage = storage.getIfAvailable();
        this.publisher = publisher.getIfAvailable();
        this.databasePurge = databasePurge;
        this.mapper = new ObjectMapper();
        this.enabled = enabled;
        this.backupVerified = backupVerified;
        this.rocketMq = "rocketmq".equals(transport);
        this.bucket = requireBucket(bucket);
    }

    @Autowired
    public DataRetentionTaskPublisher(
            DataRetentionDeliveryService service,
            ObjectProvider<ObjectStoragePort> storage,
            ObjectProvider<MessagePublisherPort> publisher,
            ObjectProvider<DataRetentionDatabasePurgePort> databasePurge,
            @Value("${foodmate.retention.execution.enabled:false}") boolean enabled,
            @Value("${foodmate.runtime.transport:http}") String transport,
            @Value("${foodmate.storage.bucket:foodmate-private}") String bucket,
            @Value("${foodmate.retention.execution.backup-verified:false}")
                    boolean backupVerified) {
        this(
                service,
                storage,
                publisher,
                databasePurge.getIfAvailable(),
                enabled,
                transport,
                bucket,
                backupVerified);
    }

    @Scheduled(fixedDelayString = "${foodmate.retention.execution-poll-ms:1000}")
    public void publishPending() {
        if (!enabled || !backupVerified) return;
        for (PurgeTaskSnapshot task : service.pending(20)) {
            if (!task.hardDeleteEnabled()) continue;
            String owner = "retention_" + UUID.randomUUID();
            if (service.lease(task.taskId(), owner, task.resourceType(), task.resourceId()) != 1)
                continue;
            try {
                if ("object_storage".equals(task.taskType())) {
                    executeObjectDelete(task, owner);
                } else if ("vector_index".equals(task.taskType())) {
                    publishVectorDelete(task, owner);
                } else if ("database".equals(task.taskType())) {
                    executeDatabasePurge(task, owner);
                } else {
                    throw new IllegalArgumentException("retention task type is invalid");
                }
            } catch (RuntimeException exception) {
                service.retry(
                        task.taskId(), owner, "RETENTION_TASK_FAILED", safeMessage(exception));
            }
        }
    }

    private void executeDatabasePurge(PurgeTaskSnapshot task, String owner) {
        if (databasePurge == null) {
            throw new IllegalStateException("database purge is unavailable");
        }
        DataRetentionDatabasePurgePort.PurgeResult result =
                databasePurge.purgeWithResult(task.resourceType(), task.resourceId());
        if (result == null || !result.verifiedAbsent()) {
            throw new IllegalStateException("database purge could not verify resource absence");
        }
        service.succeeded(
                new DataRetentionDeliveryService.PurgeExecution(
                        task.taskId(),
                        owner,
                        version(task),
                        result.backend(),
                        result.deletedCount(),
                        true));
    }

    private void executeObjectDelete(PurgeTaskSnapshot task, String owner) {
        if (storage == null) throw new IllegalStateException("object storage is unavailable");
        JsonNode target = parseTarget(task.targetRef());
        if (!bucket.equals(target.path("bucket").asText())
                || !safeObjectKey(target.path("key").asText()))
            throw new IllegalArgumentException("retention object target is invalid");
        String key = target.path("key").asText();
        boolean existed = storage.exists(bucket, key);
        storage.delete(bucket, target.path("key").asText());
        if (storage.exists(bucket, key)) {
            throw new IllegalStateException(
                    "object storage purge could not verify resource absence");
        }
        service.succeeded(
                new DataRetentionDeliveryService.PurgeExecution(
                        task.taskId(), owner, version(task), "minio", existed ? 1 : 0, true));
    }

    private void publishVectorDelete(PurgeTaskSnapshot task, String owner) {
        if (!rocketMq || publisher == null)
            throw new IllegalStateException("retention vector publisher is unavailable");
        MessagePublisherPort.PublishResult result =
                publisher.publish(
                        new MessagePublisherPort.PublishRequest(
                                task.topic(),
                                Long.toString(task.requestId()),
                                task.targetRef(),
                                MessageProperties.of(
                                        new MessageProperties.Property(
                                                "foodmate_message_type", "KnowledgePurge"),
                                        new MessageProperties.Property(
                                                "foodmate_retention_request_id",
                                                Long.toString(task.requestId())))));
        if (result == null || result.messageId() == null || result.messageId().isBlank())
            throw new IllegalStateException("retention vector publish was not confirmed");
        service.published(task.taskId(), owner, result.messageId());
    }

    private JsonNode parseTarget(String value) {
        try {
            return mapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("retention task target is invalid", exception);
        }
    }

    private boolean safeObjectKey(String value) {
        return value != null
                && !value.isBlank()
                && value.length() <= 512
                && !value.startsWith("/")
                && !value.contains("..")
                && !value.contains("\\")
                && value.matches("[A-Za-z0-9._/-]+");
    }

    private String version(PurgeTaskSnapshot task) {
        JsonNode target = parseTarget(task.targetRef());
        return target.path("version").asText("");
    }

    private static String safeMessage(Exception exception) {
        String value = exception.getMessage();
        String text = value == null ? exception.getClass().getSimpleName() : value;
        return text.substring(0, Math.min(256, text.length()));
    }

    private static String requireBucket(String value) {
        if (value == null || value.isBlank() || !value.matches("[A-Za-z0-9._-]+"))
            throw new IllegalArgumentException("storage bucket is invalid");
        return value;
    }
}
