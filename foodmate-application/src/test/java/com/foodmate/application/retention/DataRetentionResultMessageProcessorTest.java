package com.foodmate.application.retention;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.foodmate.application.retention.messaging.DataRetentionResultMessageProcessor;
import com.foodmate.application.retention.service.DataRetentionDeliveryService;
import com.foodmate.application.runtime.messaging.MessageProperties;
import com.foodmate.application.runtime.messaging.MqConsumeDecision;
import com.foodmate.application.runtime.messaging.MqMessageHandler.MqMessageContext;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class DataRetentionResultMessageProcessorTest {
    @Test
    void validVectorPurgeResultIsAcceptedWithBoundedErrorSummary() {
        DataRetentionDeliveryService service = Mockito.mock(DataRetentionDeliveryService.class);
        DataRetentionResultMessageProcessor processor =
                new DataRetentionResultMessageProcessor(service);

        assertEquals(
                MqConsumeDecision.ACK,
                processor.handle(
                        "{\"task_id\":103,\"status\":\"failed\",\"error_code\":\"RAG_MILVUS_DELETE_FAILED\",\"error_summary\":\"backend unavailable\"}",
                        context()));

        verify(service)
                .acceptResult(
                        eq(103L),
                        eq("failed"),
                        eq("RAG_MILVUS_DELETE_FAILED"),
                        eq("backend unavailable"));
    }

    @Test
    void malformedOrUnsafePurgeResultIsRejectedBeforePersistence() {
        DataRetentionDeliveryService service = Mockito.mock(DataRetentionDeliveryService.class);
        DataRetentionResultMessageProcessor processor =
                new DataRetentionResultMessageProcessor(service);

        assertEquals(
                MqConsumeDecision.REJECT,
                processor.handle("{\"task_id\":0,\"status\":\"succeeded\"}", context()));
        assertEquals(
                MqConsumeDecision.REJECT,
                processor.handle("{\"task_id\":103,\"status\":\"failed\"}", context()));
        assertEquals(MqConsumeDecision.REJECT, processor.handle("not-json", context()));

        verifyNoInteractions(service);
    }

    private static MqMessageContext context() {
        return new MqMessageContext(
                "foodmate-knowledge-purge-result-v1",
                "message-1",
                "103",
                0,
                MessageProperties.empty());
    }
}
