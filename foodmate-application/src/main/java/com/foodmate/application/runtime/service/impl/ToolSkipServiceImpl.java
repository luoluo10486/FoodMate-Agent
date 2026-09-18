package com.foodmate.application.runtime.service.impl;

import com.foodmate.application.common.service.OperationAuditService;
import com.foodmate.application.runtime.port.out.CancellationRepository;
import com.foodmate.application.runtime.port.out.InboxRepository;
import com.foodmate.application.runtime.port.out.RuntimeClientPort;
import com.foodmate.application.runtime.port.out.ToolSkipRepository;
import com.foodmate.application.runtime.service.ToolRegistryService;
import com.foodmate.application.runtime.service.ToolSkipService;
import com.foodmate.application.runtime.service.V1RuntimeEventService;
import com.foodmate.shared.error.BusinessException;
import com.foodmate.shared.error.ErrorCode;
import com.foodmate.shared.id.IdGenerator;
import com.foodmate.shared.runtime.V1SkipCommand;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 用户跳过工具步骤的应用服务；执行和跳过通过 Proposal Inbox 的 CAS 竞争执行权。 */
@Service
public class ToolSkipServiceImpl implements ToolSkipService {
    private final ToolSkipRepository skips;
    private final InboxRepository inbox;
    private final CancellationRepository dispatches;
    private final ToolRegistryService registry;
    private final IdGenerator ids;
    private final RuntimeClientPort client;
    private final V1RuntimeEventService events;
    private final OperationAuditService audit;

    public ToolSkipServiceImpl(
            ToolSkipRepository skips,
            InboxRepository inbox,
            CancellationRepository dispatches,
            ToolRegistryService registry,
            IdGenerator ids,
            ObjectProvider<RuntimeClientPort> clientProvider,
            V1RuntimeEventService events,
            ObjectProvider<OperationAuditService> auditProvider) {
        this.skips = skips;
        this.inbox = inbox;
        this.dispatches = dispatches;
        this.registry = registry;
        this.ids = ids;
        this.client = clientProvider == null ? null : clientProvider.getIfAvailable();
        this.events = events;
        this.audit = auditProvider == null ? null : auditProvider.getIfAvailable();
    }

    @Transactional
    @Override
    public SkipResult request(long userId, String runId, String proposalId, String reason) {
        try {
            return requestInternal(userId, runId, proposalId, reason);
        } catch (RuntimeException exception) {
            if (audit != null)
                audit.recordFailure(
                        userId,
                        "agent_run",
                        runId,
                        "agent_run.tool_skip",
                        "failed",
                        errorCode(exception),
                        null,
                        null,
                        Map.of("proposal_id", safe(proposalId)));
            throw exception;
        }
    }

    private SkipResult requestInternal(
            long userId, String runId, String proposalId, String reason) {
        required(runId, "run_id");
        required(proposalId, "proposal_id");
        String normalizedReason = requiredReason(reason);
        events.requireRunOwner(runId, userId);
        long numericRunId = parseRunId(runId);

        InboxRepository.InboxRecord proposal = inbox.find(proposalId);
        if (proposal == null || !runId.equals(proposal.runId()))
            throw new BusinessException(ErrorCode.TOOL_STEP_NOT_FOUND);
        if (proposal.dispatchId() == null
                || proposal.attempt() < 1
                || proposal.invocationId() == null
                || proposal.toolName() == null)
            throw new BusinessException(ErrorCode.TOOL_STEP_STALE);

        CancellationRepository.ActiveDispatch active = dispatches.findActiveDispatch(numericRunId);
        if (active == null
                || !proposal.dispatchId().equals(active.dispatchId())
                || proposal.attempt() != active.attempt())
            throw new BusinessException(ErrorCode.TOOL_STEP_STALE);

        ToolRegistryService.ToolView tool = registry.resolve(proposal.toolName(), null);
        if (!tool.skippable()) throw new BusinessException(ErrorCode.TOOL_SKIP_NOT_ALLOWED);

        ToolSkipRepository.SkipRecord existing = skips.findByProposalId(proposalId);
        if (existing != null) return existingResult(existing, normalizedReason);

        if ("executing".equals(proposal.status()))
            throw new BusinessException(ErrorCode.TOOL_STEP_ALREADY_RUNNING);
        if ("completed".equals(proposal.status()) || "failed".equals(proposal.status()))
            throw new BusinessException(ErrorCode.TOOL_STEP_ALREADY_RESOLVED);
        if (!"claimed".equals(proposal.status()))
            throw new BusinessException(ErrorCode.TOOL_STEP_STALE);

        String skipId = "skip_" + UUID.randomUUID().toString().replace("-", "");
        Instant requestedAt = Instant.now();
        String requestHash =
                digest(
                        runId
                                + "|"
                                + proposal.dispatchId()
                                + "|"
                                + proposal.attempt()
                                + "|"
                                + proposalId
                                + "|"
                                + normalizedReason);
        if (inbox.requestSkip(proposalId) != 1) throw stateChanged(proposalId);

        skips.insertRequested(
                new ToolSkipRepository.NewSkip(
                        ids.nextId(),
                        skipId,
                        numericRunId,
                        proposalId,
                        proposal.invocationId(),
                        proposal.toolName(),
                        proposal.dispatchId(),
                        proposal.attempt(),
                        requestHash,
                        proposal.requestHash(),
                        normalizedReason,
                        requestedAt));
        SkipResult result =
                new SkipResult(
                        runId,
                        proposalId,
                        skipId,
                        proposal.dispatchId(),
                        proposal.attempt(),
                        "requested");
        if (audit != null)
            audit.record(
                    userId,
                    "agent_run",
                    runId,
                    "agent_run.tool_skip",
                    "success",
                    null,
                    requestHash,
                    null,
                    Map.of(
                            "proposal_id", proposalId,
                            "skip_id", skipId,
                            "dispatch_id", proposal.dispatchId(),
                            "attempt", proposal.attempt(),
                            "tool_name", proposal.toolName()));
        return result;
    }

    @Scheduled(fixedDelayString = "${foodmate.runtime.tool-skip-poll-ms:500}")
    @Override
    public void publishRequested() {
        if (client == null) return;
        for (ToolSkipRepository.PendingSkip pending : skips.findRequested(10)) {
            try {
                V1SkipCommand command =
                        new V1SkipCommand(
                                "v1",
                                pending.runId(),
                                pending.dispatchId(),
                                pending.attempt(),
                                pending.skipId(),
                                pending.proposalId(),
                                pending.invocationId(),
                                pending.toolName(),
                                "req_skip_" + pending.skipId(),
                                "trace_skip_" + pending.runId(),
                                pending.requestHash(),
                                pending.proposalRequestHash(),
                                pending.requestedAt().plusSeconds(30),
                                pending.reason(),
                                pending.requestedAt());
                RuntimeClientPort.Response response = client.skip(command);
                skips.markDispatched(
                        pending.rowId(),
                        response.messageId() == null ? "http" : "rocketmq",
                        response.messageId());
            } catch (RuntimeException ignored) {
                // Runtime 暂时不可用时保留 requested，下一轮定时任务继续使用同一命令重试。
            }
        }
    }

    private SkipResult existingResult(
            ToolSkipRepository.SkipRecord existing, String requestedReason) {
        if (!requestedReason.equals(existing.reason()))
            throw new BusinessException(ErrorCode.CONFLICT, "同一工具步骤的跳过原因不能变化");
        return new SkipResult(
                existing.runId(),
                existing.proposalId(),
                existing.skipId(),
                existing.dispatchId(),
                existing.attempt(),
                existing.status());
    }

    private BusinessException stateChanged(String proposalId) {
        InboxRepository.InboxRecord current = inbox.find(proposalId);
        if (current == null) return new BusinessException(ErrorCode.TOOL_STEP_NOT_FOUND);
        if ("executing".equals(current.status()))
            return new BusinessException(ErrorCode.TOOL_STEP_ALREADY_RUNNING);
        if ("completed".equals(current.status()) || "failed".equals(current.status()))
            return new BusinessException(ErrorCode.TOOL_STEP_ALREADY_RESOLVED);
        return new BusinessException(ErrorCode.TOOL_STEP_STALE);
    }

    private static String errorCode(RuntimeException exception) {
        if (exception instanceof BusinessException business) return business.errorCode().code();
        return "TOOL_STEP_SKIP_FAILED";
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank() || value.length() > 128)
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, name + " 无效");
        return value.trim();
    }

    private static String requiredReason(String value) {
        if (value == null || value.isBlank() || value.length() > 256)
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "跳过原因无效");
        return value.trim();
    }

    private static long parseRunId(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new BusinessException(ErrorCode.TOOL_STEP_NOT_FOUND, "运行不存在");
        }
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

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
