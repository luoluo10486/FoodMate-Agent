package com.foodmate.application.knowledge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.foodmate.application.knowledge.messaging.KnowledgeIndexResultMessageProcessor;
import com.foodmate.application.knowledge.port.out.KnowledgeRepository;
import com.foodmate.application.knowledge.service.KnowledgeDeliveryService;
import com.foodmate.application.runtime.messaging.MessageProperties;
import com.foodmate.application.runtime.messaging.MqConsumeDecision;
import com.foodmate.application.runtime.messaging.MqMessageHandler.MqMessageContext;
import java.lang.reflect.RecordComponent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class KnowledgeIndexResultMessageProcessorTest {
    private final KnowledgeDeliveryService delivery =
            org.mockito.Mockito.mock(KnowledgeDeliveryService.class);
    private final KnowledgeIndexResultMessageProcessor processor =
            new KnowledgeIndexResultMessageProcessor(delivery);

    @Test
    void indexedResultIsAcceptedWithSafeIndexSummary() {
        String body =
                "{\"item_id\":11,\"document_id\":12,\"version\":\"2026-08\","
                        + "\"status\":\"indexed\",\"chunk_count\":1,\"attempt\":2,"
                        + "\"chunks\":[{\"chunk_no\":0,\"embedding_id\":\"emb-1\",\"section_path\":\"Guide\",\"text\":\"Protein guide\"}],"
                        + "\"token_count\":123,\"cost_amount\":\"0.12\",\"model_version\":\"stub-v1\"}";

        assertEquals(MqConsumeDecision.ACK, processor.handle(body, context()));

        ArgumentCaptor<KnowledgeRepository.IndexResult> result =
                ArgumentCaptor.forClass(KnowledgeRepository.IndexResult.class);
        verify(delivery).accept(result.capture(), any(String.class));
        assertEquals(11L, result.getValue().itemId());
        assertEquals(12L, result.getValue().documentId());
        assertEquals("2026-08", result.getValue().version());
        assertEquals(2, result.getValue().attempt());
        assertEquals(123L, result.getValue().tokenCount());
        assertEquals("0.12", result.getValue().costAmount().toPlainString());
        assertEquals("stub-v1", result.getValue().modelVersion());
        assertEquals(1, result.getValue().chunks().size());
        assertEquals("emb-1", result.getValue().chunks().get(0).embeddingId());
    }

    @Test
    void malformedOrUnsafeResultIsRejectedBeforePersistence() {
        assertEquals(
                MqConsumeDecision.REJECT,
                processor.handle(
                        "{\"item_id\":11,\"document_id\":12,\"version\":\"\","
                                + "\"status\":\"indexed\",\"attempt\":1}",
                        context()));
        assertEquals(
                MqConsumeDecision.REJECT,
                processor.handle(
                        "{\"item_id\":11,\"document_id\":12,\"version\":\"v1\","
                                + "\"status\":\"index_failed\",\"attempt\":4,"
                                + "\"error_code\":\"RAG_FAILED\"}",
                        context()));
        verifyNoInteractions(delivery);
    }

    @Test
    void indexedResultWithoutChunkFactsIsRejected() {
        String body =
                "{\"item_id\":11,\"document_id\":12,\"version\":\"v1\","
                        + "\"status\":\"indexed\",\"chunk_count\":1,\"attempt\":1,"
                        + "\"token_count\":1,\"model_version\":\"stub-v1\"}";

        assertEquals(MqConsumeDecision.REJECT, processor.handle(body, context()));
        verifyNoInteractions(delivery);
    }

    @Test
    void invalidUsageSummaryIsRejectedBeforePersistence() {
        String negativeTokens =
                "{\"item_id\":11,\"document_id\":12,\"version\":\"v1\","
                        + "\"status\":\"indexed\",\"chunk_count\":1,\"attempt\":1,"
                        + "\"token_count\":-1,\"model_version\":\"stub-v1\"}";
        String missingModel =
                "{\"item_id\":11,\"document_id\":12,\"version\":\"v1\","
                        + "\"status\":\"indexed\",\"chunk_count\":1,\"attempt\":1,"
                        + "\"token_count\":1}";
        String negativeCost =
                "{\"item_id\":11,\"document_id\":12,\"version\":\"v1\","
                        + "\"status\":\"indexed\",\"chunk_count\":1,\"attempt\":1,"
                        + "\"token_count\":1,\"cost_amount\":\"-0.01\","
                        + "\"model_version\":\"stub-v1\"}";

        assertEquals(MqConsumeDecision.REJECT, processor.handle(negativeTokens, context()));
        assertEquals(MqConsumeDecision.REJECT, processor.handle(missingModel, context()));
        assertEquals(MqConsumeDecision.REJECT, processor.handle(negativeCost, context()));
        verifyNoInteractions(delivery);
    }

    @Test
    void failedResultCarriesTheBoundedSafetySummaryToPersistence() throws Exception {
        String body =
                "{\"item_id\":11,\"document_id\":12,\"version\":\"v1\","
                        + "\"status\":\"index_failed\",\"attempt\":3,"
                        + "\"error_code\":\"RAG_PARSE_FAILED\","
                        + "\"error_summary\":\"document parser rejected the file\"}";

        assertEquals(MqConsumeDecision.ACK, processor.handle(body, context()));

        ArgumentCaptor<KnowledgeRepository.IndexResult> result =
                ArgumentCaptor.forClass(KnowledgeRepository.IndexResult.class);
        verify(delivery).accept(result.capture(), any(String.class));
        RecordComponent summary =
                java.util.Arrays.stream(result.getValue().getClass().getRecordComponents())
                        .filter(component -> component.getName().equals("errorSummary"))
                        .findFirst()
                        .orElseThrow();
        assertEquals(
                "document parser rejected the file",
                summary.getAccessor().invoke(result.getValue()));
    }

    private static MqMessageContext context() {
        return new MqMessageContext(
                "knowledge-result", "message-1", "11", 0, MessageProperties.empty());
    }
}
