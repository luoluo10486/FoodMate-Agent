package com.foodmate.application.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.foodmate.gateway.MqMessageHandler.MqMessageContext;
import com.foodmate.gateway.MqConsumeDecision;
import com.foodmate.gateway.RocketMqSettings;
import com.foodmate.shared.id.IdGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.Message;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;

class RuntimeProposalMessageProcessorTest {
    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();
    private static final RocketMqSettings SETTINGS = new RocketMqSettings(
            "localhost:9876", "command", "event", "proposal", "result",
            "java-events", "java-proposals", "java-producer", 1000, 3, 3);

    @Test
    void resultPublishFailureReturnsRetryButCompletedInboxPreventsSecondSqlExecution() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ToolGatewayService gateway = mock(ToolGatewayService.class);
        DefaultMQProducer producer = mock(DefaultMQProducer.class);
        RuntimeProposalMessageProcessor processor = new RuntimeProposalMessageProcessor(
                gateway, producer, SETTINGS, provider(jdbc));
        String body = body("proposal-1", "sha256:one");
        var result = new ToolGatewayService.ProposalResult("proposal-1", "42", "succeeded", null, List.of());
        when(gateway.execute(any())).thenReturn(result);
        when(jdbc.update(startsWith("INSERT INTO runtime_tool_proposal_inbox"), any(Object[].class)))
                .thenReturn(1);
        when(jdbc.update(startsWith("UPDATE runtime_tool_proposal_inbox"), any(Object[].class))).thenReturn(1);
        doThrow(new RuntimeException("broker down")).when(producer).send(any(Message.class));

        assertEquals(MqConsumeDecision.RETRY, processor.handle(body, context()));

        when(jdbc.update(startsWith("INSERT INTO runtime_tool_proposal_inbox"), any(Object[].class)))
                .thenReturn(0);
        String resultJson = MAPPER.writeValueAsString(result);
        when(jdbc.queryForMap(anyString(), eq("proposal-1"))).thenReturn(Map.of(
                "request_hash", "sha256:one", "status", "completed", "result_json", resultJson));
        reset(producer);
        when(producer.send(any(Message.class))).thenReturn(null);

        assertEquals(MqConsumeDecision.ACK, processor.handle(body, context()));
        verify(gateway, times(1)).execute(any());
        verify(producer, times(1)).send(any(Message.class));
    }

    @Test
    void sameProposalIdWithDifferentHashIsRejectedWithoutExecutingTool() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        ToolGatewayService gateway = mock(ToolGatewayService.class);
        DefaultMQProducer producer = mock(DefaultMQProducer.class);
        RuntimeProposalMessageProcessor processor = new RuntimeProposalMessageProcessor(
                gateway, producer, SETTINGS, provider(jdbc));
        when(jdbc.update(startsWith("INSERT INTO runtime_tool_proposal_inbox"), any(), any(), any(), any()))
                .thenReturn(0);
        when(jdbc.queryForMap(anyString(), eq("proposal-1"))).thenReturn(Map.of(
                "request_hash", "sha256:original", "status", "completed",
                "result_json", "{\"proposalId\":\"proposal-1\",\"runId\":\"42\",\"status\":\"succeeded\",\"rows\":[]}"));

        assertEquals(MqConsumeDecision.REJECT, processor.handle(body("proposal-1", "sha256:changed"), context()));
        verifyNoInteractions(gateway, producer);
    }

    private static String body(String proposalId, String requestHash) {
        return "{\"proposal_id\":\"" + proposalId + "\",\"request_hash\":\"" + requestHash
                + "\",\"run_id\":\"42\",\"proposal_type\":\"sql_read\",\"payload\":{\"statement\":\"SELECT 1\"}}";
    }

    private static MqMessageContext context() {
        return new MqMessageContext("proposal", "message-1", "42", 0, Map.of());
    }

    private static ObjectProvider<JdbcTemplate> provider(JdbcTemplate value) {
        return new ObjectProvider<>() {
            public JdbcTemplate getObject(Object... args) { return value; }
            public JdbcTemplate getIfAvailable() { return value; }
            public JdbcTemplate getIfUnique() { return value; }
            public Stream<JdbcTemplate> orderedStream() { return Stream.of(value); }
            public Stream<JdbcTemplate> stream() { return Stream.of(value); }
        };
    }
}
