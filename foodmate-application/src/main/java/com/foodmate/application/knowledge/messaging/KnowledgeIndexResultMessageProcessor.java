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
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/** Consumes Python indexing results and applies them to the Java-owned knowledge state. */
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
            List<KnowledgeRepository.IndexChunk> chunks = parseChunks(node.path("chunks"));
            if (!("indexed".equals(status) || "index_failed".equals(status))
                    || itemId <= 0
                    || documentId <= 0
                    || version.isBlank()
                    || attempt < 1
                    || attempt > 3
                    || chunkCount < 0
                    || tokenCount < 0
                    || ("indexed".equals(status)
                            && (modelVersion == null
                                    || modelVersion.isBlank()
                                    || chunkCount == 0
                                    || chunks.size() != chunkCount))
                    || ("index_failed".equals(status) && !chunks.isEmpty())
                    || ("index_failed".equals(status)
                            && (errorCode == null || errorCode.isBlank())))
                return MqConsumeDecision.REJECT;
            BigDecimal costAmount = new BigDecimal(node.path("cost_amount").asText("0"));
            if (costAmount.signum() < 0) return MqConsumeDecision.REJECT;
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
                            modelVersion,
                            chunks),
                    hash(body));
            return MqConsumeDecision.ACK;
        } catch (com.fasterxml.jackson.core.JsonProcessingException
                | IllegalArgumentException error) {
            return MqConsumeDecision.REJECT;
        } catch (RuntimeException error) {
            return MqConsumeDecision.RETRY;
        }
    }

    private List<KnowledgeRepository.IndexChunk> parseChunks(JsonNode value) {
        if (!value.isArray() || value.size() > 1024) return List.of();
        List<KnowledgeRepository.IndexChunk> chunks = new ArrayList<>();
        for (int index = 0; index < value.size(); index++) {
            JsonNode chunk = value.get(index);
            int chunkNo = chunk.path("chunk_no").asInt(-1);
            String embeddingId = chunk.path("embedding_id").asText("").trim();
            String sectionPath = chunk.path("section_path").asText("");
            String text = chunk.path("text").asText("");
            if (chunkNo != index
                    || embeddingId.isBlank()
                    || embeddingId.length() > 128
                    || sectionPath.length() > 255
                    || text.isBlank()
                    || text.length() > 900) return List.of();
            chunks.add(new KnowledgeRepository.IndexChunk(chunkNo, embeddingId, sectionPath, text));
        }
        return List.copyOf(chunks);
    }

    private String hash(String body) {
        try {
            byte[] bytes =
                    MessageDigest.getInstance("SHA-256")
                            .digest(body.getBytes(StandardCharsets.UTF_8));
            StringBuilder value = new StringBuilder("sha256:");
            for (byte item : bytes) value.append(String.format("%02x", item));
            return value.toString();
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }
}
