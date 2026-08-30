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

/** 消费 Python 索引结果，并以幂等方式更新 Java 所拥有的知识库状态。 */
@Service
public class KnowledgeIndexResultMessageProcessor implements MqMessageHandler {
    private final KnowledgeDeliveryService service;
    private final ObjectMapper mapper = new ObjectMapper();

    public KnowledgeIndexResultMessageProcessor(KnowledgeDeliveryService service) {
        this.service = service;
    }

    /** 校验并幂等应用一条 Python 索引结果消息。 */
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
            String errorSummary =
                    safeErrorSummary(node.path("error_summary").asText(""), errorCode);
            String modelVersion = node.path("model_version").asText(null);
            String providerTraceId = optionalTraceId(node.get("provider_trace_id"));
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
                            errorSummary,
                            attempt,
                            tokenCount,
                            costAmount,
                            modelVersion,
                            providerTraceId,
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

    private String safeErrorSummary(String value, String errorCode) {
        String normalized = value == null ? "" : value.replaceAll("[\\r\\n\\t]+", " ").trim();
        if (normalized.isBlank()) normalized = errorCode == null ? "INDEX_FAILED" : errorCode;
        normalized =
                normalized.replaceAll(
                        "(?i)(api[_ -]?key|authorization|bearer|password|token)\\s*[:=]\\s*\\S+",
                        "$1=[REDACTED]");
        return normalized.length() <= 512 ? normalized : normalized.substring(0, 512);
    }

    private String optionalTraceId(JsonNode value) {
        if (value == null || value.isNull()) return null;
        String normalized = value.asText("").trim();
        if (normalized.isBlank()
                || normalized.length() > 256
                || normalized.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("provider trace id is invalid");
        }
        return normalized;
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
