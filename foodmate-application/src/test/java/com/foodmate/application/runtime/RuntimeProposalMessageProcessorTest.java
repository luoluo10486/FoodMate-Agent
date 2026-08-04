package com.foodmate.application.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodmate.application.runtime.messaging.MqConsumeDecision;
import com.foodmate.application.runtime.messaging.MqMessageHandler.MqMessageContext;
import com.foodmate.application.runtime.port.out.InboxRepository;
import com.foodmate.application.runtime.port.out.MessagePublisherPort;
import com.foodmate.application.runtime.processor.RuntimeProposalMessageProcessor;
import com.foodmate.application.runtime.service.ToolGatewayService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RuntimeProposalMessageProcessorTest {
    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    @Test
    void resultPublishFailureReturnsRetryButCompletedInboxPreventsSecondSqlExecution()
            throws Exception {
        InboxRepository inbox = mock(InboxRepository.class);
        ToolGatewayService gateway = mock(ToolGatewayService.class);
        MessagePublisherPort publisher = mock(MessagePublisherPort.class);
        RuntimeProposalMessageProcessor processor =
                new RuntimeProposalMessageProcessor(gateway, publisher, inbox, "result");
        String body = body("proposal-1", "sha256:one");
        var result =
                new ToolGatewayService.ProposalResult(
                        "proposal-1", "42", "succeeded", null, List.of());
        when(gateway.executeLegacy(any())).thenReturn(result);
        when(inbox.claim(eq("proposal-1"), eq("sha256:one"), eq(body))).thenReturn(1);
        when(inbox.complete(eq("proposal-1"), anyString())).thenReturn(1);
        doThrow(new RuntimeException("broker down"))
                .when(publisher)
                .publish(any(MessagePublisherPort.PublishRequest.class));

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
        reset(publisher);
        when(publisher.publish(any(MessagePublisherPort.PublishRequest.class)))
                .thenReturn(new MessagePublisherPort.PublishResult("message-1"));

        assertEquals(MqConsumeDecision.ACK, processor.handle(body, context()));
        verify(gateway, times(1)).executeLegacy(any());
        verify(publisher, times(1)).publish(any(MessagePublisherPort.PublishRequest.class));
    }

    @Test
    void sameProposalIdWithDifferentHashIsRejectedWithoutExecutingTool() {
        InboxRepository inbox = mock(InboxRepository.class);
        ToolGatewayService gateway = mock(ToolGatewayService.class);
        MessagePublisherPort publisher = mock(MessagePublisherPort.class);
        RuntimeProposalMessageProcessor processor =
                new RuntimeProposalMessageProcessor(gateway, publisher, inbox, "result");
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
        verifyNoInteractions(gateway, publisher);
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
