package com.foodmate.application.runtime.processor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodmate.application.common.service.AgentOperationMetrics;
import com.foodmate.application.runtime.messaging.MessageProperties;
import com.foodmate.application.runtime.messaging.MqConsumeDecision;
import com.foodmate.application.runtime.messaging.MqMessageHandler;
import com.foodmate.application.runtime.messaging.MqMessageHandler.MqMessageContext;
import com.foodmate.application.runtime.port.out.InboxRepository;
import com.foodmate.application.runtime.port.out.MessagePublisherPort;
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

/** Proposal topic consumer with durable idempotency and typed V1 wire messages. */
@Service
@ConditionalOnBean(MessagePublisherPort.class)
public class RuntimeProposalMessageProcessor implements MqMessageHandler {
    private static final Logger log =
            LoggerFactory.getLogger(RuntimeProposalMessageProcessor.class);
    private final ToolGatewayService gateway;
    private final MessagePublisherPort publisher;
    private final String resultTopic;
    private final InboxRepository inbox;
    private final AgentOperationMetrics metrics;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    public RuntimeProposalMessageProcessor(
            ToolGatewayService gateway,
            MessagePublisherPort publisher,
            InboxRepository inbox,
            @Value("${foodmate.runtime.rocketmq.result-topic:foodmate-agent-result-v1}")
                    String resultTopic) {
        this(gateway, publisher, inbox, resultTopic, null);
    }

    @Autowired
    public RuntimeProposalMessageProcessor(
            ToolGatewayService gateway,
            MessagePublisherPort publisher,
            InboxRepository inbox,
            @Value("${foodmate.runtime.rocketmq.result-topic:foodmate-agent-result-v1}")
                    String resultTopic,
            ObjectProvider<AgentOperationMetrics> metricsProvider) {
        this.gateway = gateway;
        this.publisher = publisher;
        this.inbox = inbox;
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
            ToolGatewayService.ProposalResult result;
            String existing = claimOrExisting(proposalId, requestHash, body);
            if (existing != null) {
                result = mapper.readValue(existing, ToolGatewayService.ProposalResult.class);
                if (metrics != null) metrics.count("rocketmq", "proposal", "duplicate", "inbox");
            } else {
                result =
                        gateway.execute(
                                new ToolGatewayService.ProposalCommand(
                                        proposal.proposalId(),
                                        proposal.runId(),
                                        proposal.proposalType(),
                                        proposal.schemaVersion(),
                                        proposal.toolName(),
                                        proposal.confirmationRef(),
                                        proposal.input(),
                                        proposal.payload() == null
                                                ? null
                                                : new ToolGatewayService.ProposalPayload(
                                                        proposal.payload().statement(),
                                                        proposal.payload().invocationId(),
                                                        proposal.payload().idempotencyKey())));
                inbox.complete(proposalId, mapper.writeValueAsString(result));
                if (metrics != null)
                    metrics.count(
                            "rocketmq",
                            "proposal",
                            result.status() == null ? "success" : result.status(),
                            result.errorCode() == null ? "completed" : result.errorCode());
            }
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
                                    result.sqlAuditId()));
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
            } catch (Exception publishFailure) {
                log.warn(
                        "Proposal result publish failed: proposal_id={}, error_type={}, message={}",
                        proposalId,
                        publishFailure.getClass().getSimpleName(),
                        publishFailure.getMessage());
                return MqConsumeDecision.RETRY;
            }
            return MqConsumeDecision.ACK;
        } catch (IllegalArgumentException exception) {
            return MqConsumeDecision.REJECT;
        } catch (Exception exception) {
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
        } catch (Exception ignored) {
            return "unknown";
        }
    }

    private String claimOrExisting(String proposalId, String requestHash, String body) {
        int inserted = inbox.claim(proposalId, requestHash, body);
        if (inserted == 1) return null;
        InboxRepository.InboxRecord existing = inbox.find(proposalId);
        if (existing == null || !requestHash.equals(existing.requestHash()))
            throw new IllegalArgumentException("proposal idempotency conflict");
        if ("completed".equals(existing.status()) && existing.resultJson() != null)
            return existing.resultJson();
        throw new IllegalStateException("proposal execution is incomplete");
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
