package com.foodmate.application.food.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodmate.application.food.port.out.ApprovalRequestRepository;
import com.foodmate.application.food.service.ApprovalService;
import com.foodmate.application.food.service.FoodLogService;
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
    private final FoodLogService foods;
    private final IdGenerator ids;
    private final ObjectMapper mapper;

    public ApprovalServiceImpl(
            ApprovalRequestRepository store,
            MealPlanService plans,
            IdGenerator ids,
            ObjectMapper mapper) {
        this(store, plans, null, ids, mapper);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public ApprovalServiceImpl(
            ApprovalRequestRepository store,
            MealPlanService plans,
            FoodLogService foods,
            IdGenerator ids,
            ObjectMapper mapper) {
        this.store = store;
        this.plans = plans;
        this.foods = foods;
        this.ids = ids;
        this.mapper = mapper.copy().findAndRegisterModules();
    }

    @Override
    public String parametersDigest(
            String operation, String resourceType, Long resourceId, JsonNode parameters) {
        return digest(operation, resourceType, resourceId, parameters);
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
        requireDigest(approval, digestForExecution(approval, parameters));
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
        return executeInternal(userId, null, approvalRequestId, null, parameters);
    }

    @Override
    @Transactional
    public ExecuteView executeForAgent(
            long userId,
            long agentRunId,
            long approvalRequestId,
            String idempotencyKey,
            JsonNode parameters) {
        return executeInternal(userId, agentRunId, approvalRequestId, idempotencyKey, parameters);
    }

    private ExecuteView executeInternal(
            long userId,
            Long expectedAgentRunId,
            long approvalRequestId,
            String expectedIdempotencyKey,
            JsonNode parameters) {
        ApprovalRequestRepository.ApprovalSnapshot approval = require(userId, approvalRequestId);
        if (expectedAgentRunId != null && !expectedAgentRunId.equals(approval.agentRunId()))
            throw new BusinessException(ErrorCode.FORBIDDEN, "确认请求不属于当前 AgentRun");
        if (expectedIdempotencyKey != null
                && !expectedIdempotencyKey.equals(approval.idempotencyKey()))
            throw toolConflict("工具幂等键与确认事实不一致", "TOOL_IDEMPOTENCY_CONFLICT");
        requireOperation(approval);
        requireDigest(approval, digestForExecution(approval, parameters));
        if ("executed".equals(approval.status()))
            return new ExecuteView(
                    approval.approvalRequestId(),
                    approval.operation(),
                    approval.status(),
                    approval.resourceId());
        ensurePendingOrConfirmed(approval, userId);
        if (!"confirmed".equals(approval.status()))
            throw toolConflict("写操作尚未确认", "TOOL_CONFIRMATION_REQUIRED");
        if ("save_plan".equals(approval.operation())) {
            if (approval.resourceId() == null)
                throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "save_plan 缺少计划 ID");
        } else if (!"create".equals(approval.operation())
                || !"food_log".equals(approval.resourceType())
                || foods == null) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "当前不支持该确认写操作");
        }
        if (store.markExecuted(userId, approvalRequestId, Instant.now()) != 1)
            throw new BusinessException(ErrorCode.CONFLICT, "确认请求执行状态已变化");
        // 状态占用和业务写入处于同一事务，避免并发执行重复调用业务写入。
        Long resourceId = approval.resourceId();
        if ("save_plan".equals(approval.operation())) {
            plans.save(userId, approval.resourceId(), businessSaveKey(approval.idempotencyKey()));
        } else {
            resourceId =
                    foods.create(
                                    userId,
                                    new FoodLogService.CreateCommand(
                                            approval.sessionId(),
                                            approval.agentRunId(),
                                            instant(parameters, "meal_time"),
                                            com.foodmate.shared.food.enums.MealType.fromCode(
                                                    parameters.path("meal_type").asText()),
                                            text(parameters, "notes"),
                                            businessFoodLogKey(approval.idempotencyKey()),
                                            "agent",
                                            foodItems(parameters)))
                            .foodLogId();
            if (store.updateExecutedResource(userId, approvalRequestId, resourceId, Instant.now())
                    != 1) {
                throw new BusinessException(ErrorCode.CONFLICT, "确认执行结果未能绑定饮食记录");
            }
        }
        audit(
                approvalRequestId,
                userId,
                trace(approval),
                "approval.execute",
                approval.parametersDigest(),
                approval.idempotencyKey() + ":execute");
        return new ExecuteView(approvalRequestId, approval.operation(), "executed", resourceId);
    }

    private void validateProposal(ProposalCommand command) {
        if (command == null
                || command.operation() == null
                || command.resourceType() == null
                || command.idempotencyKey() == null
                || command.parameters() == null)
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "确认请求参数不完整");
        boolean mealPlan =
                "save_plan".equals(command.operation())
                        && "meal_plan".equals(command.resourceType());
        boolean foodLog =
                "create".equals(command.operation()) && "food_log".equals(command.resourceType());
        if (!mealPlan && !foodLog)
            throw new BusinessException(
                    ErrorCode.INVALID_ARGUMENT, "当前只支持 meal_plan.save_plan 或 food_log.create");
        if (mealPlan && (command.resourceId() == null || command.resourceId() <= 0))
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "确认资源 ID 无效");
        if (foodLog && command.resourceId() != null)
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "food_log.create 不应绑定已有资源");
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
        boolean mealPlan =
                "save_plan".equals(approval.operation())
                        && "meal_plan".equals(approval.resourceType());
        boolean foodLog =
                "create".equals(approval.operation()) && "food_log".equals(approval.resourceType());
        if (!mealPlan && !foodLog)
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "当前不支持该确认写操作");
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
            throw toolConflict("确认请求已过期", "TOOL_CONFIRMATION_REQUIRED");
        }
        if (!"pending".equals(approval.status()) && !"confirmed".equals(approval.status()))
            throw toolConflict("确认请求状态不可执行", "TOOL_POLICY_DENIED");
    }

    private void requireDigest(ApprovalRequestRepository.ApprovalSnapshot approval, String digest) {
        if (!approval.parametersDigest().equals(digest))
            throw toolConflict("确认参数已变化，请重新提议", "TOOL_CONFIRMATION_REQUIRED");
    }

    private String digestForExecution(
            ApprovalRequestRepository.ApprovalSnapshot approval, JsonNode parameters) {
        Long resourceId =
                "create".equals(approval.operation()) && "food_log".equals(approval.resourceType())
                        ? null
                        : approval.resourceId();
        return digest(approval.operation(), approval.resourceType(), resourceId, parameters);
    }

    private BusinessException toolConflict(String message, String toolErrorCode) {
        return new BusinessException(
                ErrorCode.CONFLICT,
                message,
                mapper.createObjectNode().put("tool_error_code", toolErrorCode));
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

    private String businessSaveKey(String approvalKey) {
        return "plan_" + digest("meal_plan.save", approvalKey);
    }

    private String businessFoodLogKey(String approvalKey) {
        return "food_" + digest("food_log.create", approvalKey);
    }

    private static Instant instant(JsonNode parameters, String field) {
        String value = text(parameters, field);
        if (value == null)
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "food_log.create 缺少 " + field);
        try {
            return Instant.parse(value);
        } catch (java.time.format.DateTimeParseException exception) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "food_log.create 的时间无效");
        }
    }

    private static String text(JsonNode parameters, String field) {
        JsonNode value = parameters.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static java.util.List<FoodLogService.ItemCommand> foodItems(JsonNode parameters) {
        if (!parameters.path("items").isArray())
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "food_log.create 缺少 items");
        java.util.List<FoodLogService.ItemCommand> items = new java.util.ArrayList<>();
        for (JsonNode item : parameters.path("items")) {
            JsonNode name = item.has("name") ? item.get("name") : item.get("raw_name");
            items.add(
                    new FoodLogService.ItemCommand(
                            name == null ? null : name.asText(),
                            item.has("amount") && item.get("amount").isNumber()
                                    ? item.get("amount").decimalValue()
                                    : null,
                            text(item, "unit")));
        }
        return items;
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
