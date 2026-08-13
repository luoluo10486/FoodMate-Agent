package com.foodmate.application.food.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodmate.application.food.port.out.ApprovalRequestRepository;
import com.foodmate.application.food.service.ApprovalService;
import com.foodmate.application.food.service.MealPlanService;
import com.foodmate.shared.error.BusinessException;
import com.foodmate.shared.error.ErrorCode;
import com.foodmate.shared.id.IdGenerator;
import com.foodmate.shared.trace.TraceContext;
import com.foodmate.shared.trace.TraceContextHolder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Java 权威写确认服务；模型和 Agent 没有直接写业务表的能力。 */
@Service
@Profile("local")
public class ApprovalServiceImpl implements ApprovalService {
    private static final long MIN_EXPIRY_SECONDS = 60;
    private static final long MAX_EXPIRY_SECONDS = 3600;
    private final ApprovalRequestRepository store;
    private final MealPlanService plans;
    private final IdGenerator ids;
    private final ObjectMapper mapper;

    public ApprovalServiceImpl(
            ApprovalRequestRepository store,
            MealPlanService plans,
            IdGenerator ids,
            ObjectMapper mapper) {
        this.store = store;
        this.plans = plans;
        this.ids = ids;
        this.mapper = mapper.copy().findAndRegisterModules();
    }

    @Override
    @Transactional
    public ProposalView propose(long userId, ProposalCommand command) {
        validateProposal(command);
        String digest =
                digest(
                        command.operation(),
                        command.resourceType(),
                        command.resourceId(),
                        command.parameters());
        ApprovalRequestRepository.ApprovalSnapshot existing =
                store.findByIdempotency(userId, command.idempotencyKey());
        if (existing != null) {
            requireDigest(existing, digest);
            return view(existing);
        }
        Instant expiresAt = Instant.now().plus(command.expiresInSeconds(), ChronoUnit.SECONDS);
        TraceContext trace = TraceContextHolder.currentOrNew();
        ApprovalRequestRepository.ApprovalWrite write =
                new ApprovalRequestRepository.ApprovalWrite(
                        ids.nextId(),
                        userId,
                        command.sessionId(),
                        command.agentRunId(),
                        command.resourceType(),
                        command.resourceId(),
                        command.operation(),
                        digest,
                        trace.requestId(),
                        trace.traceId(),
                        command.idempotencyKey(),
                        expiresAt);
        if (store.insert(write) != 1) {
            ApprovalRequestRepository.ApprovalSnapshot raced =
                    store.findByIdempotency(userId, command.idempotencyKey());
            if (raced == null) throw new BusinessException(ErrorCode.CONFLICT, "确认请求创建竞争失败");
            requireDigest(raced, digest);
            return view(raced);
        }
        audit(
                write.approvalRequestId(),
                userId,
                trace,
                "approval.propose",
                digest,
                command.idempotencyKey());
        return view(require(userId, write.approvalRequestId()));
    }

    @Override
    @Transactional
    public ProposalView confirm(long userId, long approvalRequestId, JsonNode parameters) {
        ApprovalRequestRepository.ApprovalSnapshot approval = require(userId, approvalRequestId);
        requireOperation(approval);
        requireDigest(
                approval,
                digest(
                        approval.operation(),
                        approval.resourceType(),
                        approval.resourceId(),
                        parameters));
        ensurePending(approval, userId);
        if (store.markConfirmed(userId, approvalRequestId, Instant.now()) != 1)
            throw new BusinessException(ErrorCode.CONFLICT, "确认请求状态已变化");
        audit(
                approvalRequestId,
                userId,
                trace(approval),
                "approval.confirm",
                approval.parametersDigest(),
                approval.idempotencyKey() + ":confirm");
        return view(require(userId, approvalRequestId));
    }

    @Override
    @Transactional
    public ExecuteView execute(long userId, long approvalRequestId, JsonNode parameters) {
        ApprovalRequestRepository.ApprovalSnapshot approval = require(userId, approvalRequestId);
        requireOperation(approval);
        requireDigest(
                approval,
                digest(
                        approval.operation(),
                        approval.resourceType(),
                        approval.resourceId(),
                        parameters));
        if ("executed".equals(approval.status()))
            return new ExecuteView(
                    approval.approvalRequestId(),
                    approval.operation(),
                    approval.status(),
                    approval.resourceId());
        ensurePendingOrConfirmed(approval, userId);
        if (!"confirmed".equals(approval.status()))
            throw new BusinessException(ErrorCode.CONFLICT, "写操作尚未确认");
        if (!"save_plan".equals(approval.operation()))
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "当前只支持 save_plan 确认执行");
        if (approval.resourceId() == null)
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "save_plan 缺少计划 ID");
        if (store.markExecuted(userId, approvalRequestId, Instant.now()) != 1)
            throw new BusinessException(ErrorCode.CONFLICT, "确认请求执行状态已变化");
        // 状态占用和业务写入处于同一事务，避免并发执行重复调用业务写入。
        plans.save(userId, approval.resourceId());
        audit(
                approvalRequestId,
                userId,
                trace(approval),
                "approval.execute",
                approval.parametersDigest(),
                approval.idempotencyKey() + ":execute");
        return new ExecuteView(
                approvalRequestId, approval.operation(), "executed", approval.resourceId());
    }

    private void validateProposal(ProposalCommand command) {
        if (command == null
                || command.operation() == null
                || command.resourceType() == null
                || command.idempotencyKey() == null
                || command.parameters() == null)
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "确认请求参数不完整");
        if (!"save_plan".equals(command.operation()) || !"meal_plan".equals(command.resourceType()))
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "当前只支持 meal_plan.save_plan");
        if (command.resourceId() == null || command.resourceId() <= 0)
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "确认资源 ID 无效");
        if (command.idempotencyKey().isBlank() || command.idempotencyKey().length() > 128)
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "确认幂等键无效");
        if (command.expiresInSeconds() < MIN_EXPIRY_SECONDS
                || command.expiresInSeconds() > MAX_EXPIRY_SECONDS)
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "确认有效期必须在 60 到 3600 秒之间");
    }

    private ApprovalRequestRepository.ApprovalSnapshot require(long userId, long id) {
        ApprovalRequestRepository.ApprovalSnapshot value = store.findOwned(userId, id);
        if (value == null) throw new BusinessException(ErrorCode.NOT_FOUND, "确认请求不存在");
        return value;
    }

    private void requireOperation(ApprovalRequestRepository.ApprovalSnapshot approval) {
        if (!"save_plan".equals(approval.operation())
                || !"meal_plan".equals(approval.resourceType()))
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "当前只支持 meal_plan.save_plan");
    }

    private void ensurePending(ApprovalRequestRepository.ApprovalSnapshot approval, long userId) {
        if (!"pending".equals(approval.status()))
            throw new BusinessException(ErrorCode.CONFLICT, "确认请求不是 pending 状态");
        if (!Instant.now().isBefore(approval.expiresAt())) {
            store.markExpired(userId, approval.approvalRequestId(), Instant.now());
            throw new BusinessException(ErrorCode.CONFLICT, "确认请求已过期");
        }
    }

    private void ensurePendingOrConfirmed(
            ApprovalRequestRepository.ApprovalSnapshot approval, long userId) {
        if (("pending".equals(approval.status()) || "confirmed".equals(approval.status()))
                && !Instant.now().isBefore(approval.expiresAt())) {
            store.markExpired(userId, approval.approvalRequestId(), Instant.now());
            throw new BusinessException(ErrorCode.CONFLICT, "确认请求已过期");
        }
        if (!"pending".equals(approval.status()) && !"confirmed".equals(approval.status()))
            throw new BusinessException(ErrorCode.CONFLICT, "确认请求状态不可执行");
    }

    private void requireDigest(ApprovalRequestRepository.ApprovalSnapshot approval, String digest) {
        if (!approval.parametersDigest().equals(digest))
            throw new BusinessException(ErrorCode.CONFLICT, "确认参数已变化，请重新提议");
    }

    private String digest(Object... values) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(
                                            mapper.writeValueAsString(
                                                            java.util.Arrays.asList(values))
                                                    .getBytes(StandardCharsets.UTF_8)));
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "确认参数摘要计算失败");
        }
    }

    private void audit(
            long id, long userId, TraceContext trace, String action, String digest, String key) {
        store.insertAudit(
                new ApprovalRequestRepository.AuditWrite(
                        ids.nextId(),
                        userId,
                        trace.requestId(),
                        trace.traceId(),
                        "approval_request",
                        Long.toString(id),
                        action,
                        digest,
                        key,
                        "{}"));
    }

    private TraceContext trace(ApprovalRequestRepository.ApprovalSnapshot approval) {
        return TraceContext.of(approval.requestId(), approval.traceId());
    }

    private ProposalView view(ApprovalRequestRepository.ApprovalSnapshot value) {
        return new ProposalView(
                value.approvalRequestId(),
                value.operation(),
                value.resourceType(),
                value.resourceId(),
                value.parametersDigest(),
                value.status(),
                value.expiresAt(),
                value.confirmedAt(),
                value.executedAt());
    }
}
