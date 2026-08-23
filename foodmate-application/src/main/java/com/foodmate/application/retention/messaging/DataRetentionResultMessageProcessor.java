package com.foodmate.application.retention.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodmate.application.retention.service.DataRetentionDeliveryService;
import com.foodmate.application.runtime.messaging.MqConsumeDecision;
import com.foodmate.application.runtime.messaging.MqMessageHandler;
import org.springframework.stereotype.Service;

/** Consumes Python vector purge facts and converges the task idempotently. */
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
            long taskId = node.path("task_id").asLong(0);
            String status = node.path("status").asText("");
            String errorCode = node.path("error_code").asText("");
            String errorSummary = node.path("error_summary").asText("");
            if (taskId <= 0
                    || !("succeeded".equals(status) || "failed".equals(status))
                    || ("failed".equals(status) && errorCode.isBlank()))
                return MqConsumeDecision.REJECT;
            service.acceptResult(taskId, status, errorCode, safe(errorSummary));
            return MqConsumeDecision.ACK;
        } catch (com.fasterxml.jackson.core.JsonProcessingException
                | IllegalArgumentException error) {
            return MqConsumeDecision.REJECT;
        } catch (RuntimeException error) {
            return MqConsumeDecision.RETRY;
        }
    }

    private String safe(String value) {
        return value == null ? "" : value.substring(0, Math.min(256, value.length()));
    }
}
