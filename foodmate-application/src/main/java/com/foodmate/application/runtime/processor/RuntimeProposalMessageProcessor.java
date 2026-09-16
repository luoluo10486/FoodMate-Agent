package com.foodmate.application.runtime.processor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodmate.application.common.service.AgentOperationMetrics;
import com.foodmate.application.runtime.messaging.MessageProperties;
import com.foodmate.application.runtime.messaging.MqConsumeDecision;
import com.foodmate.application.runtime.messaging.MqMessageHandler;
import com.foodmate.application.runtime.messaging.MqMessageHandler.MqMessageContext;
import com.foodmate.application.runtime.port.out.MessagePublisherPort;
import com.foodmate.application.runtime.service.RuntimeProposalService;
import com.foodmate.application.runtime.service.ToolGatewayService;
import com.foodmate.shared.runtime.V1ToolProposal;
import com.foodmate.shared.runtime.V1ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

/** 具备持久化幂等和强类型 V1 消息的提案 Topic 消费者。 */
@Service
@ConditionalOnBean(MessagePublisherPort.class)
public class RuntimeProposalMessageProcessor implements MqMessageHandler {
    private static final Logger log =
            LoggerFactory.getLogger(RuntimeProposalMessageProcessor.class);
    private final RuntimeProposalService proposals;
    private final MessagePublisherPort publisher;
    private final String resultTopic;
    private final AgentOperationMetrics metrics;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    public RuntimeProposalMessageProcessor(
            RuntimeProposalService proposals,
            MessagePublisherPort publisher,
            @Value("${foodmate.runtime.rocketmq.result-topic:foodmate-agent-result-v1}")
                    String resultTopic) {
        this(proposals, publisher, resultTopic, null);
    }

    @Autowired
    public RuntimeProposalMessageProcessor(
            RuntimeProposalService proposals,
            MessagePublisherPort publisher,
            @Value("${foodmate.runtime.rocketmq.result-topic:foodmate-agent-result-v1}")
                    String resultTopic,
            ObjectProvider<AgentOperationMetrics> metricsProvider) {
        this.proposals = proposals;
        this.publisher = publisher;
        this.resultTopic = resultTopic;
        this.metrics = metricsProvider == null ? null : metricsProvider.getIfAvailable();
    }

    @Override
    public MqConsumeDecision handle(String body, MqMessageContext context) {
        try {
            V1ToolProposal proposal = mapper.readValue(body, V1ToolProposal.class);
            String proposalId = requiredText(proposal.proposalId());
            String requestHash = requiredText(proposal.requestHash());
            String invocationId = invocationId(proposal.payload());
            ToolGatewayService.ProposalResult result = proposals.execute(proposal, body);
            if (metrics != null)
                metrics.count(
                        "rocketmq",
                        "proposal",
                        result.status() == null ? "success" : result.status(),
                        result.errorCode() == null ? "completed" : result.errorCode());
            String payload =
                    mapper.writeValueAsString(
                            new V1ToolResult(
                                    "v1",
                                    result.proposalId() == null ? "" : result.proposalId(),
                                    requestHash,
                                    result.runId() == null ? "" : result.runId(),
                                    invocationId,
                                    result.status(),
                                    result.errorCode() == null ? "" : result.errorCode(),
                                    result.rows(),
                                    result.sqlAuditId(),
                                    result.toolName(),
                                    result.confirmationRef()));
            try {
                MessagePublisherPort.PublishResult published =
                        publisher.publish(
                                new MessagePublisherPort.PublishRequest(
                                        resultTopic,
                                        result.runId() == null
                                                ? context.messageId()
                                                : result.runId(),
                                        payload,
                                        MessageProperties.of(
                                                new MessageProperties.Property(
                                                        "foodmate_proposal_id",
                                                        result.proposalId() == null
                                                                ? ""
                                                                : result.proposalId()))));
                log.info(
                        "Proposal result published: proposal_id={}, msg_id={}, send_status={}",
                        proposalId,
                        published == null ? null : published.messageId(),
                        published == null ? null : "confirmed");
            } catch (RuntimeException publishFailure) {
                log.warn(
                        "Proposal result publish failed: proposal_id={}, error_type={}, message={}",
                        proposalId,
                        publishFailure.getClass().getSimpleName(),
                        publishFailure.getMessage());
                return MqConsumeDecision.RETRY;
            }
            return MqConsumeDecision.ACK;
        } catch (JsonProcessingException exception) {
            log.warn(
                    "Proposal message contract is invalid, rejecting: error_type={}, message={}",
                    exception.getClass().getSimpleName(),
                    exception.getMessage());
            return MqConsumeDecision.REJECT;
        } catch (IllegalArgumentException exception) {
            return MqConsumeDecision.REJECT;
        } catch (RuntimeException exception) {
            if (metrics != null) metrics.count("rocketmq", "result", "failed", "consumer_error");
            log.warn(
                    "Proposal processing failed, will retry: proposal_id={}, error_type={}, message={}",
                    safeId(body),
                    exception.getClass().getSimpleName(),
                    exception.getMessage());
            return MqConsumeDecision.RETRY;
        }
    }

    private String safeId(String body) {
        try {
            return mapper.readTree(body).path("proposal_id").asText("unknown");
        } catch (JsonProcessingException ignored) {
            return "unknown";
        }
    }

    private static String requiredText(String value) {
        if (value == null || value.isBlank() || value.length() > 128)
            throw new IllegalArgumentException("proposal contract is invalid");
        return value;
    }

    private static String invocationId(V1ToolProposal.Payload payload) {
        if (payload == null) throw new IllegalArgumentException("proposal payload is invalid");
        return requiredText(payload.invocationId());
    }
}
