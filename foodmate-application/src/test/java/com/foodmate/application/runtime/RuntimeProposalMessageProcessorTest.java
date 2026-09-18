package com.foodmate.application.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.foodmate.application.runtime.messaging.MessageProperties;
import com.foodmate.application.runtime.messaging.MqConsumeDecision;
import com.foodmate.application.runtime.messaging.MqMessageHandler.MqMessageContext;
import com.foodmate.application.runtime.port.out.MessagePublisherPort;
import com.foodmate.application.runtime.processor.RuntimeProposalMessageProcessor;
import com.foodmate.application.runtime.service.RuntimeProposalService;
import com.foodmate.application.runtime.service.ToolGatewayService;
import com.foodmate.shared.runtime.V1ToolProposal;
import java.util.List;
import org.junit.jupiter.api.Test;

class RuntimeProposalMessageProcessorTest {
    @Test
    void resultPublishFailureReturnsRetryButCompletedInboxPreventsSecondSqlExecution()
            throws Exception {
        RuntimeProposalService proposals = mock(RuntimeProposalService.class);
        MessagePublisherPort publisher = mock(MessagePublisherPort.class);
        RuntimeProposalMessageProcessor processor =
                new RuntimeProposalMessageProcessor(proposals, publisher, "result");
        String body = body("proposal-1", "sha256:one");
        var result =
                new ToolGatewayService.ProposalResult(
                        "proposal-1", "42", "succeeded", null, List.of());
        when(proposals.execute(any(V1ToolProposal.class), eq(body))).thenReturn(result);
        doThrow(new RuntimeException("broker down"))
                .when(publisher)
                .publish(any(MessagePublisherPort.PublishRequest.class));

        assertEquals(MqConsumeDecision.RETRY, processor.handle(body, context()));

        reset(publisher);
        when(publisher.publish(any(MessagePublisherPort.PublishRequest.class)))
                .thenReturn(new MessagePublisherPort.PublishResult("message-1"));

        assertEquals(MqConsumeDecision.ACK, processor.handle(body, context()));
        verify(proposals, times(2)).execute(any(V1ToolProposal.class), eq(body));
        verify(publisher, times(1)).publish(any(MessagePublisherPort.PublishRequest.class));
    }

    @Test
    void sameProposalIdWithDifferentHashIsRejectedWithoutExecutingTool() {
        RuntimeProposalService proposals = mock(RuntimeProposalService.class);
        MessagePublisherPort publisher = mock(MessagePublisherPort.class);
        RuntimeProposalMessageProcessor processor =
                new RuntimeProposalMessageProcessor(proposals, publisher, "result");
        doThrow(new IllegalArgumentException("proposal idempotency conflict"))
                .when(proposals)
                .execute(any(V1ToolProposal.class), anyString());

        assertEquals(
                MqConsumeDecision.REJECT,
                processor.handle(body("proposal-1", "sha256:changed"), context()));
        verifyNoInteractions(publisher);
    }

    private static String body(String proposalId, String requestHash) {
        return "{\"proposal_id\":\""
                + proposalId
                + "\",\"request_hash\":\""
                + requestHash
                + "\",\"run_id\":\"42\",\"proposal_type\":\"sql_read\",\"payload\":{\"statement\":\"SELECT 1\",\"invocation_id\":\"invocation-1\"}}";
    }

    private static MqMessageContext context() {
        return new MqMessageContext("proposal", "message-1", "42", 0, MessageProperties.empty());
    }
}
