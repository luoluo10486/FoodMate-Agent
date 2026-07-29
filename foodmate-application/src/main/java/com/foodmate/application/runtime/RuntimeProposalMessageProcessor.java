package com.foodmate.application.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodmate.application.runtime.persistence.ProposalInboxStore;
import com.foodmate.gateway.MqConsumeDecision;
import com.foodmate.gateway.MqMessageHandler;
import com.foodmate.gateway.RocketMqSettings;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.Message;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Proposal Topic 消费器：事务完成并发布 Result 后才 ACK，重复 Proposal 由业务幂等键隔离。 */
@Service
@ConditionalOnBean(DefaultMQProducer.class)
public class RuntimeProposalMessageProcessor implements MqMessageHandler {
    private static final Logger log = LoggerFactory.getLogger(RuntimeProposalMessageProcessor.class);
    private final ToolGatewayService gateway;
    private final DefaultMQProducer producer;
    private final RocketMqSettings settings;
    private final ProposalInboxStore inbox;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    public RuntimeProposalMessageProcessor(
            ToolGatewayService gateway,
            DefaultMQProducer producer,
            RocketMqSettings settings,
            ProposalInboxStore inbox) {
        this.gateway = gateway;
        this.producer = producer;
        this.settings = settings;
        this.inbox = inbox;
    }

    @Override
    public MqConsumeDecision handle(String body, MqMessageContext context) {
        try {
            Map<String, Object> proposal = mapper.readValue(body, Map.class);
            String proposalId = requiredText(proposal.get("proposal_id"));
            String requestHash = requiredText(proposal.get("request_hash"));
            ToolGatewayService.ProposalResult result;
            {
                String existing = claimOrExisting(proposalId, requestHash, body);
                if (existing != null) {
                    result = mapper.readValue(existing, ToolGatewayService.ProposalResult.class);
                } else {
                    result = gateway.execute(proposal);
                    inbox.complete(proposalId, mapper.writeValueAsString(result));
                }
            }
            String payload =
                    mapper.writeValueAsString(
                            Map.of(
                                    "schema_version",
                                    "v1",
                                    "proposal_id",
                                    result.proposalId() == null ? "" : result.proposalId(),
                                    "request_hash",
                                    requestHash,
                                    "run_id",
                                    result.runId() == null ? "" : result.runId(),
                                    "status",
                                    result.status(),
                                    "error_code",
                                    result.errorCode() == null ? "" : result.errorCode(),
                                    "rows",
                                    result.rows()));
            Message message =
                    new Message(settings.resultTopic(), payload.getBytes(StandardCharsets.UTF_8));
            message.setKeys(result.runId() == null ? context.messageId() : result.runId());
            message.putUserProperty(
                    "foodmate_proposal_id", result.proposalId() == null ? "" : result.proposalId());
            // Inbox 已经固化执行结果，但 Broker 尚未确认时必须 RETRY；下次消费会重发同一个 Result，不能再次执行工具。
            try {
                producer.send(message);
            } catch (RuntimeException publishFailure) {
                return MqConsumeDecision.RETRY;
            }
            return MqConsumeDecision.ACK;
        } catch (IllegalArgumentException exception) {
            return MqConsumeDecision.REJECT;
        } catch (Exception exception) {
            // 这里只记录标识和异常类型，不记录 Proposal 正文，便于定位重试原因并避免泄漏用户数据。
            log.warn("Proposal processing failed, will retry: proposal_id={}, error_type={}, message={}",
                    safeId(body), exception.getClass().getSimpleName(), exception.getMessage());
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

    /** 返回已固化的 Result；首次消费返回 null，允许执行一次。 */
    private String claimOrExisting(String proposalId, String requestHash, String body) {
        try {
            int inserted = inbox.claim(proposalId, requestHash, body);
            if (inserted == 1) return null;
            Map<String, Object> existing = inbox.find(proposalId);
            if (!requestHash.equals(existing.get("request_hash")))
                throw new IllegalArgumentException("proposal idempotency conflict");
            if ("completed".equals(existing.get("status")) && existing.get("result_json") != null)
                return existing.get("result_json").toString();
            throw new IllegalStateException("proposal execution is incomplete");
        } catch (org.springframework.dao.DataAccessException exception) {
            throw exception;
        }
    }

    private static String requiredText(Object value) {
        if (value == null || value.toString().isBlank() || value.toString().length() > 128) {
            throw new IllegalArgumentException("proposal contract is invalid");
        }
        return value.toString();
    }
}
