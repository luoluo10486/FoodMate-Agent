package com.foodmate.application.knowledge.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodmate.application.knowledge.port.out.KnowledgeRepository;
import com.foodmate.application.knowledge.service.KnowledgeDeliveryService;
import com.foodmate.application.runtime.messaging.MqConsumeDecision;
import com.foodmate.application.runtime.messaging.MqMessageHandler;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeIndexResultMessageProcessor implements MqMessageHandler {
    private final KnowledgeDeliveryService service;
    private final ObjectMapper mapper = new ObjectMapper();

    public KnowledgeIndexResultMessageProcessor(KnowledgeDeliveryService service) {
        this.service = service;
    }

    @Override
    public MqConsumeDecision handle(String body, MqMessageContext context) {
        try {
            JsonNode node = mapper.readTree(body);
            String status = node.path("status").asText();
            long itemId = node.path("item_id").asLong(0);
            long documentId = node.path("document_id").asLong(0);
            String version = node.path("version").asText("").trim();
            int attempt = node.path("attempt").asInt(0);
            int chunkCount = node.path("chunk_count").asInt(0);
            long tokenCount = node.path("token_count").asLong(0);
            String errorCode = node.path("error_code").asText(null);
            String modelVersion = node.path("model_version").asText(null);
            if (!("indexed".equals(status) || "index_failed".equals(status))
                    || itemId <= 0
                    || documentId <= 0
                    || version.isBlank()
                    || attempt < 1
                    || attempt > 3
                    || chunkCount < 0
                    || tokenCount < 0
                    || ("indexed".equals(status)
                            && (modelVersion == null || modelVersion.isBlank()))
                    || ("index_failed".equals(status)
                            && (errorCode == null || errorCode.isBlank())))
                return MqConsumeDecision.REJECT;
            BigDecimal costAmount = new BigDecimal(node.path("cost_amount").asText("0"));
            if (costAmount.signum() < 0)
                return MqConsumeDecision.REJECT;
            service.accept(
                    new KnowledgeRepository.IndexResult(
                            itemId,
                            documentId,
                            version,
                            status,
                            chunkCount,
                            errorCode,
                            attempt,
                            tokenCount,
                            costAmount,
                            modelVersion),
                    hash(body));
            return MqConsumeDecision.ACK;
        } catch (com.fasterxml.jackson.core.JsonProcessingException
                | IllegalArgumentException error) {
            return MqConsumeDecision.REJECT;
        } catch (RuntimeException error) {
            return MqConsumeDecision.RETRY;
        }
    }

    private String hash(String body) {
        try {
            byte[] bytes =
                    MessageDigest.getInstance("SHA-256")
                            .digest(body.getBytes(StandardCharsets.UTF_8));
            StringBuilder value = new StringBuilder("sha256:");
            for (byte item : bytes) value.append(String.format("%02x", item));
            return value.toString();
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }
}
