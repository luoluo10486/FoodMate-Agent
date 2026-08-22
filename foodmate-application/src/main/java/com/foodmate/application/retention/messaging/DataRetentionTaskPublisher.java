package com.foodmate.application.retention.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodmate.application.common.port.out.ObjectStoragePort;
import com.foodmate.application.retention.port.out.DataRetentionRepository.PurgeTaskSnapshot;
import com.foodmate.application.retention.service.DataRetentionDeliveryService;
import com.foodmate.application.runtime.messaging.MessageProperties;
import com.foodmate.application.runtime.port.out.MessagePublisherPort;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Executes only approved external cleanup steps when the explicit local switch is enabled. */
@Component
public class DataRetentionTaskPublisher {
    private final DataRetentionDeliveryService service;
    private final ObjectStoragePort storage;
    private final MessagePublisherPort publisher;
    private final ObjectMapper mapper;
    private final boolean enabled;
    private final boolean rocketMq;
    private final String bucket;

    public DataRetentionTaskPublisher(
            DataRetentionDeliveryService service,
            ObjectProvider<ObjectStoragePort> storage,
            ObjectProvider<MessagePublisherPort> publisher,
            @Value("${foodmate.retention.execution.enabled:false}") boolean enabled,
            @Value("${foodmate.runtime.transport:http}") String transport,
            @Value("${foodmate.storage.bucket:foodmate-private}") String bucket) {
        this.service = service;
        this.storage = storage.getIfAvailable();
        this.publisher = publisher.getIfAvailable();
        this.mapper = new ObjectMapper();
        this.enabled = enabled;
        this.rocketMq = "rocketmq".equals(transport);
        this.bucket = requireBucket(bucket);
    }

    @Scheduled(fixedDelayString = "${foodmate.retention.execution-poll-ms:1000}")
    public void publishPending() {
        if (!enabled) return;
        for (PurgeTaskSnapshot task : service.pending(20)) {
            if (!task.hardDeleteEnabled()) continue;
            if ("database".equals(task.taskType())) continue;
            String owner = "retention_" + UUID.randomUUID();
            if (service.lease(task.taskId(), owner, task.resourceType(), task.resourceId()) != 1)
                continue;
            try {
                if ("object_storage".equals(task.taskType())) {
                    executeObjectDelete(task, owner);
                } else if ("vector_index".equals(task.taskType())) {
                    publishVectorDelete(task, owner);
                } else {
                    throw new IllegalArgumentException("retention task type is invalid");
                }
            } catch (RuntimeException exception) {
                service.retry(
                        task.taskId(), owner, "RETENTION_TASK_FAILED", safeMessage(exception));
            }
        }
    }

    private void executeObjectDelete(PurgeTaskSnapshot task, String owner) {
        if (storage == null) throw new IllegalStateException("object storage is unavailable");
        JsonNode target = parseTarget(task.targetRef());
        if (!bucket.equals(target.path("bucket").asText())
                || !safeObjectKey(target.path("key").asText()))
            throw new IllegalArgumentException("retention object target is invalid");
        storage.delete(bucket, target.path("key").asText());
        service.succeeded(task.taskId(), owner, "", "");
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
