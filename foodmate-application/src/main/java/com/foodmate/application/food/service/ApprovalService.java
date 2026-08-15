package com.foodmate.application.food.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;

/** Agent 和自然语言写操作的 Proposal -> Confirm -> Execute 用例。 */
public interface ApprovalService {
    ProposalView propose(long userId, ProposalCommand command);

    String parametersDigest(
            String operation, String resourceType, Long resourceId, JsonNode parameters);

    ProposalView confirm(long userId, long approvalRequestId, JsonNode parameters);

    ProposalView reject(long userId, long approvalRequestId, JsonNode parameters);

    ExecuteView execute(long userId, long approvalRequestId, JsonNode parameters);

    ExecuteView executeForAgent(
            long userId,
            long agentRunId,
            long approvalRequestId,
            String idempotencyKey,
            JsonNode parameters);

    record ProposalCommand(
            Long sessionId,
            Long agentRunId,
            String operation,
            String resourceType,
            Long resourceId,
            JsonNode parameters,
            String idempotencyKey,
            long expiresInSeconds) {}

    record ProposalView(
            long approvalRequestId,
            String operation,
            String resourceType,
            Long resourceId,
            String parametersDigest,
            String status,
            Instant expiresAt,
            Instant confirmedAt,
            Instant executedAt) {}

    record ExecuteView(long approvalRequestId, String operation, String status, Long resourceId) {}
}
