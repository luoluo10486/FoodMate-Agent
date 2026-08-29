package com.foodmate.application.retention.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodmate.application.retention.service.DataRetentionDeliveryService;
import com.foodmate.application.retention.service.DataRetentionDeliveryService.ExternalResult;
import com.foodmate.application.runtime.messaging.MqConsumeDecision;
import com.foodmate.application.runtime.messaging.MqMessageHandler;
import org.springframework.stereotype.Service;

/** 消费 Python 向量清理事实，并以幂等方式收敛任务状态。 */
@Service
public class DataRetentionResultMessageProcessor implements MqMessageHandler {
    private final DataRetentionDeliveryService service;
    private final ObjectMapper mapper = new ObjectMapper();

    public DataRetentionResultMessageProcessor(DataRetentionDeliveryService service) {
        this.service = service;
    }

    @Override
    public MqConsumeDecision handle(String body, MqMessageContext context) {
        try {
            JsonNode node = mapper.readTree(body);
            if (context == null
                    || !"foodmate-knowledge-purge-result-v1".equals(context.topic())
                    || context.messageId() == null
                    || context.messageId().isBlank()) return MqConsumeDecision.REJECT;
            long taskId = node.path("task_id").asLong(0);
            long requestId = node.path("request_id").asLong(0);
            long resourceId = node.path("resource_id").asLong(0);
            String resourceType = node.path("resource_type").asText("");
            String taskType = node.path("task_type").asText("");
            String documentId = node.path("document_id").asText("");
            String version = node.path("version").asText("");
            String status = node.path("status").asText("");
            String backend = node.path("backend").asText("");
            int deletedCount = node.path("deleted_count").asInt(-1);
            boolean verifiedAbsent = node.path("verified_absent").asBoolean(false);
            String errorCode = node.path("error_code").asText("");
            String errorSummary = node.path("error_summary").asText("");
            if (taskId <= 0
                    || requestId <= 0
                    || resourceId <= 0
                    || !"knowledge_document".equals(resourceType)
                    || !"vector_index".equals(taskType)
                    || documentId.isBlank()
                    || version.isBlank()
                    || !("succeeded".equals(status) || "failed".equals(status))
                    || backend.isBlank()
                    || deletedCount < 0
                    || ("succeeded".equals(status) && !verifiedAbsent)
                    || ("failed".equals(status) && errorCode.isBlank()))
                return MqConsumeDecision.REJECT;
            service.acceptResult(
                    new ExternalResult(
                            taskId,
                            requestId,
                            resourceType,
                            resourceId,
                            taskType,
                            version,
                            status,
                            safe(backend, 64),
                            deletedCount,
                            verifiedAbsent,
                            context.messageId(),
                            safe(errorCode, 64),
                            safe(errorSummary, 512)));
            return MqConsumeDecision.ACK;
        } catch (com.fasterxml.jackson.core.JsonProcessingException
                | IllegalArgumentException error) {
            return MqConsumeDecision.REJECT;
        } catch (RuntimeException error) {
            return MqConsumeDecision.RETRY;
        }
    }

    private String safe(String value, int limit) {
        return value == null ? "" : value.substring(0, Math.min(limit, value.length()));
    }
}
