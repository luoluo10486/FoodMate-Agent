package com.foodmate.application.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodmate.application.runtime.port.out.InboxRepository;
import com.foodmate.application.runtime.processor.RuntimeProposalMessageProcessor;
import com.foodmate.application.runtime.service.ToolGatewayService;
import com.foodmate.gateway.MqConsumeDecision;
import com.foodmate.gateway.MqMessageHandler.MqMessageContext;
import com.foodmate.gateway.RocketMqSettings;
import java.util.List;
import java.util.Map;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.Message;
import org.junit.jupiter.api.Test;

class RuntimeProposalMessageProcessorTest {
    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();
    private static final RocketMqSettings SETTINGS =
            new RocketMqSettings(
                    "localhost:9876",
                    "command",
                    "event",
                    "proposal",
                    "result",
                    "java-events",
                    "java-proposals",
                    "java-producer",
                    1000,
                    3,
                    3);

    @Test
    void resultPublishFailureReturnsRetryButCompletedInboxPreventsSecondSqlExecution()
            throws Exception {
        InboxRepository inbox = mock(InboxRepository.class);
        ToolGatewayService gateway = mock(ToolGatewayService.class);
        DefaultMQProducer producer = mock(DefaultMQProducer.class);
        RuntimeProposalMessageProcessor processor =
                new RuntimeProposalMessageProcessor(gateway, producer, SETTINGS, inbox);
        String body = body("proposal-1", "sha256:one");
        var result =
                new ToolGatewayService.ProposalResult(
                        "proposal-1", "42", "succeeded", null, List.of());
        when(gateway.executeLegacy(any())).thenReturn(result);
        when(inbox.claim(eq("proposal-1"), eq("sha256:one"), eq(body))).thenReturn(1);
        when(inbox.complete(eq("proposal-1"), anyString())).thenReturn(1);
        doThrow(new RuntimeException("broker down")).when(producer).send(any(Message.class));

        assertEquals(MqConsumeDecision.RETRY, processor.handle(body, context()));

        when(inbox.claim(eq("proposal-1"), eq("sha256:one"), eq(body))).thenReturn(0);
        String resultJson = MAPPER.writeValueAsString(result);
        when(inbox.find("proposal-1"))
                .thenReturn(
                        Map.of(
                                "request_hash",
                                "sha256:one",
                                "status",
                                "completed",
                                "result_json",
                                resultJson));
        reset(producer);
        when(producer.send(any(Message.class))).thenReturn(null);

        assertEquals(MqConsumeDecision.ACK, processor.handle(body, context()));
        verify(gateway, times(1)).executeLegacy(any());
        verify(producer, times(1)).send(any(Message.class));
    }

    @Test
    void sameProposalIdWithDifferentHashIsRejectedWithoutExecutingTool() {
        InboxRepository inbox = mock(InboxRepository.class);
        ToolGatewayService gateway = mock(ToolGatewayService.class);
        DefaultMQProducer producer = mock(DefaultMQProducer.class);
        RuntimeProposalMessageProcessor processor =
                new RuntimeProposalMessageProcessor(gateway, producer, SETTINGS, inbox);
        when(inbox.claim(eq("proposal-1"), eq("sha256:changed"), anyString())).thenReturn(0);
        when(inbox.find("proposal-1"))
                .thenReturn(
                        Map.of(
                                "request_hash",
                                "sha256:original",
                                "status",
                                "completed",
                                "result_json",
                                "{\"proposalId\":\"proposal-1\",\"runId\":\"42\",\"status\":\"succeeded\",\"rows\":[]}"));

        assertEquals(
                MqConsumeDecision.REJECT,
                processor.handle(body("proposal-1", "sha256:changed"), context()));
        verifyNoInteractions(gateway, producer);
    }

    private static String body(String proposalId, String requestHash) {
        return "{\"proposal_id\":\""
                + proposalId
                + "\",\"request_hash\":\""
                + requestHash
                + "\",\"run_id\":\"42\",\"proposal_type\":\"sql_read\",\"payload\":{\"statement\":\"SELECT 1\",\"invocation_id\":\"invocation-1\"}}";
    }

    private static MqMessageContext context() {
        return new MqMessageContext("proposal", "message-1", "42", 0, Map.of());
    }
}
