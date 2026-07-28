package com.foodmate.application.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodmate.gateway.MqConsumeDecision;
import com.foodmate.gateway.MqMessageHandler;
import com.foodmate.gateway.RocketMqSettings;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.Message;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

/** Proposal Topic 消费器：事务完成并发布 Result 后才 ACK，重复 Proposal 由业务幂等键隔离。 */
@Service
@ConditionalOnBean(DefaultMQProducer.class)
public class RuntimeProposalMessageProcessor implements MqMessageHandler {
    private final ToolGatewayService gateway;
    private final DefaultMQProducer producer;
    private final RocketMqSettings settings;
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    public RuntimeProposalMessageProcessor(ToolGatewayService gateway, DefaultMQProducer producer, RocketMqSettings settings,
                                           ObjectProvider<JdbcTemplate> jdbcProvider) {
        this.gateway = gateway;
        this.producer = producer;
        this.settings = settings;
        this.jdbc = jdbcProvider.getIfAvailable();
    }

    @Override
    public MqConsumeDecision handle(String body, MqMessageContext context) {
        try {
            Map<String, Object> proposal = mapper.readValue(body, Map.class);
            String proposalId = requiredText(proposal.get("proposal_id"));
            String requestHash = requiredText(proposal.get("request_hash"));
            ToolGatewayService.ProposalResult result;
            if (jdbc != null) {
                String existing = claimOrExisting(proposalId, requestHash, body);
                if (existing != null) {
                    result = mapper.readValue(existing, ToolGatewayService.ProposalResult.class);
                } else {
                    result = gateway.execute(proposal);
                    jdbc.update("UPDATE runtime_tool_proposal_inbox SET status='completed',result_json=?::jsonb,completed_at=CURRENT_TIMESTAMP WHERE proposal_id=?",
                            mapper.writeValueAsString(result), proposalId);
                }
            } else {
                result = gateway.execute(proposal);
            }
            String payload = mapper.writeValueAsString(Map.of(
                    "schema_version", "v1",
                    "proposal_id", result.proposalId() == null ? "" : result.proposalId(),
                    "request_hash", requestHash,
                    "run_id", result.runId() == null ? "" : result.runId(),
                    "status", result.status(),
                    "error_code", result.errorCode() == null ? "" : result.errorCode(),
                    "rows", result.rows()));
            Message message = new Message(settings.resultTopic(), payload.getBytes(StandardCharsets.UTF_8));
            message.setKeys(result.runId() == null ? context.messageId() : result.runId());
            message.putUserProperty("foodmate_proposal_id", result.proposalId() == null ? "" : result.proposalId());
            producer.send(message);
            return MqConsumeDecision.ACK;
        } catch (IllegalArgumentException exception) {
            return MqConsumeDecision.REJECT;
        } catch (Exception exception) {
            return MqConsumeDecision.RETRY;
        }
    }

    /** 返回已固化的 Result；首次消费返回 null，允许执行一次。 */
    private String claimOrExisting(String proposalId, String requestHash, String body) {
        try {
            int inserted = jdbc.update("INSERT INTO runtime_tool_proposal_inbox(proposal_id,request_hash,payload_json,status) VALUES (?,?,?::jsonb,'claimed') ON CONFLICT (proposal_id) DO NOTHING",
                    proposalId, requestHash, body);
            if (inserted == 1) return null;
            Map<String, Object> existing = jdbc.queryForMap("SELECT request_hash,status,result_json::text AS result_json FROM runtime_tool_proposal_inbox WHERE proposal_id=?", proposalId);
            if (!requestHash.equals(existing.get("request_hash"))) throw new IllegalArgumentException("proposal idempotency conflict");
            if ("completed".equals(existing.get("status")) && existing.get("result_json") != null) return existing.get("result_json").toString();
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
