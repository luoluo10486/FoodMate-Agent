package com.foodmate.application.runtime.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodmate.application.runtime.port.out.InboxRepository;
import com.foodmate.application.runtime.port.out.ToolSkipRepository;
import com.foodmate.application.runtime.service.RuntimeProposalService;
import com.foodmate.application.runtime.service.ToolGatewayService;
import com.foodmate.shared.runtime.V1ToolProposal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Proposal 的持久化执行协调器，防止跳过和工具执行同时获得业务执行权。 */
@Service
public class RuntimeProposalServiceImpl implements RuntimeProposalService {
    private final ToolGatewayService gateway;
    private final InboxRepository inbox;
    private final ToolSkipRepository skips;
    private final ObjectMapper mapper;

    public RuntimeProposalServiceImpl(
            ToolGatewayService gateway,
            InboxRepository inbox,
            ObjectMapper mapper,
            ObjectProvider<ToolSkipRepository> skipProvider) {
        this.gateway = gateway;
        this.inbox = inbox;
        this.mapper = mapper.copy().findAndRegisterModules();
        this.skips = skipProvider == null ? null : skipProvider.getIfAvailable();
    }

    @Override
    @Transactional
    public ToolGatewayService.ProposalResult execute(V1ToolProposal proposal, String rawPayload) {
        if (proposal == null) throw new IllegalArgumentException("proposal is required");
        String proposalId = required(proposal.proposalId(), "proposal_id");
        String payload = rawPayload == null || rawPayload.isBlank() ? json(proposal) : rawPayload;
        String requestHash =
                proposal.requestHash() == null || proposal.requestHash().isBlank()
                        ? digest(payload)
                        : required(proposal.requestHash(), "request_hash");
        V1ToolProposal effective = withRequestHash(proposal, requestHash);
        int claimed = inbox.claim(proposalId, requestHash, payload);
        if (claimed == 1) {
            ToolGatewayService.ProposalResult result = executeClaimed(effective);
            complete(proposalId, result);
            return result;
        }
        InboxRepository.InboxRecord record = inbox.find(proposalId);
        ToolGatewayService.ProposalResult result = resolveRedelivery(effective, record);
        if ("completed".equals(record.status())) return result;
        complete(proposalId, result);
        return result;
    }

    private ToolGatewayService.ProposalResult executeClaimed(V1ToolProposal proposal) {
        if (inbox.markExecuting(proposal.proposalId()) == 1)
            return gateway.execute(toCommand(proposal));
        InboxRepository.InboxRecord record = inbox.find(proposal.proposalId());
        if (record != null && "skip_requested".equals(record.status())) return skipped(proposal);
        throw new IllegalStateException("proposal execution is incomplete");
    }

    private ToolGatewayService.ProposalResult resolveRedelivery(
            V1ToolProposal proposal, InboxRepository.InboxRecord record) {
        if (record == null || !proposal.requestHash().equals(record.requestHash()))
            throw new IllegalArgumentException("proposal idempotency conflict");
        if ("completed".equals(record.status()) && record.resultJson() != null)
            return readResult(record.resultJson());
        if ("skip_requested".equals(record.status())) return skipped(proposal);
        throw new IllegalStateException("proposal execution is incomplete");
    }

    private void complete(String proposalId, ToolGatewayService.ProposalResult result) {
        if (inbox.complete(proposalId, json(result)) != 1)
            throw new IllegalStateException("proposal completion state changed");
        if ("skipped".equals(result.status()) && skips != null) skips.markApplied(proposalId);
    }

    private ToolGatewayService.ProposalResult skipped(V1ToolProposal proposal) {
        return new ToolGatewayService.ProposalResult(
                proposal.proposalId(),
                proposal.runId(),
                "skipped",
                "TOOL_STEP_SKIPPED",
                List.of(),
                null,
                proposal.toolName(),
                proposal.confirmationRef());
    }

    private ToolGatewayService.ProposalCommand toCommand(V1ToolProposal proposal) {
        V1ToolProposal.Payload payload = proposal.payload();
        return new ToolGatewayService.ProposalCommand(
                proposal.proposalId(),
                proposal.runId(),
                proposal.proposalType(),
                proposal.schemaVersion(),
                proposal.toolName(),
                proposal.confirmationRef(),
                proposal.input(),
                payload == null
                        ? null
                        : new ToolGatewayService.ProposalPayload(
                                payload.statement(),
                                payload.invocationId(),
                                payload.idempotencyKey()));
    }

    private ToolGatewayService.ProposalResult readResult(String value) {
        try {
            return mapper.readValue(value, ToolGatewayService.ProposalResult.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("stored proposal result is invalid", exception);
        }
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("proposal payload is not JSON", exception);
        }
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank() || value.length() > 128)
            throw new IllegalArgumentException(name + " is invalid");
        return value;
    }

    private static V1ToolProposal withRequestHash(V1ToolProposal proposal, String requestHash) {
        return new V1ToolProposal(
                proposal.schemaVersion(),
                proposal.proposalId(),
                requestHash,
                proposal.runId(),
                proposal.proposalType(),
                proposal.requiresConfirmation(),
                proposal.toolName(),
                proposal.confirmationRef(),
                proposal.input(),
                proposal.payload(),
                proposal.dispatchId(),
                proposal.attempt());
    }

    private static String digest(String value) {
        try {
            byte[] bytes =
                    MessageDigest.getInstance("SHA-256")
                            .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(bytes.length * 2);
            for (byte item : bytes) hex.append(String.format("%02x", item));
            return "sha256:" + hex;
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }
}
