package com.foodmate.application.food.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.foodmate.application.common.service.OperationAuditService;
import com.foodmate.application.food.port.out.ApprovalRequestRepository;
import com.foodmate.application.food.service.ApprovalService;
import com.foodmate.application.food.service.FoodLogService;
import com.foodmate.application.food.service.MealPlanService;
import com.foodmate.application.runtime.service.PlanValidator;
import com.foodmate.application.runtime.service.V1RuntimeEventService;
import com.foodmate.shared.error.BusinessException;
import com.foodmate.shared.error.ErrorCode;
import com.foodmate.shared.food.enums.MealType;
import com.foodmate.shared.id.IdGenerator;
import com.foodmate.shared.trace.TraceContext;
import com.foodmate.shared.trace.TraceContextHolder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

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
    private final TransactionTemplate executionTransactions;
    private final TransactionTemplate failureTransactions;
    private final OperationAuditService auditService;
    private final V1RuntimeEventService runtimeEvents;

    public ApprovalServiceImpl(
            ApprovalRequestRepository store,
            MealPlanService plans,
            IdGenerator ids,
            ObjectMapper mapper) {
        this(store, plans, null, ids, mapper, null);
    }

    public ApprovalServiceImpl(
            ApprovalRequestRepository store,
            MealPlanService plans,
            FoodLogService foods,
            IdGenerator ids,
            ObjectMapper mapper) {
        this(store, plans, foods, ids, mapper, null, null);
    }

    public ApprovalServiceImpl(
            ApprovalRequestRepository store,
            MealPlanService plans,
            FoodLogService foods,
            IdGenerator ids,
            ObjectMapper mapper,
            PlatformTransactionManager transactionManager) {
        this(store, plans, foods, ids, mapper, transactionManager, null);
    }

    public ApprovalServiceImpl(
            ApprovalRequestRepository store,
            MealPlanService plans,
            FoodLogService foods,
            IdGenerator ids,
            ObjectMapper mapper,
            PlatformTransactionManager transactionManager,
            org.springframework.beans.factory.ObjectProvider<OperationAuditService> auditProvider) {
        this(store, plans, foods, ids, mapper, transactionManager, auditProvider, null);
    }

    @Autowired
    public ApprovalServiceImpl(
            ApprovalRequestRepository store,
            MealPlanService plans,
            FoodLogService foods,
            IdGenerator ids,
            ObjectMapper mapper,
            PlatformTransactionManager transactionManager,
            org.springframework.beans.factory.ObjectProvider<OperationAuditService> auditProvider,
            org.springframework.beans.factory.ObjectProvider<V1RuntimeEventService>
                    runtimeEventsProvider) {
        this.store = store;
        this.plans = plans;
        this.foods = foods;
        this.ids = ids;
        this.mapper = mapper.copy().findAndRegisterModules();
        this.executionTransactions = transactionTemplate(transactionManager);
        this.failureTransactions = failureTransactionTemplate(transactionManager);
        this.auditService = auditProvider == null ? null : auditProvider.getIfAvailable();
        this.runtimeEvents =
                runtimeEventsProvider == null ? null : runtimeEventsProvider.getIfAvailable();
    }

    private static TransactionTemplate transactionTemplate(PlatformTransactionManager manager) {
        if (manager == null) return null;
        TransactionTemplate template = new TransactionTemplate(manager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    private static TransactionTemplate failureTransactionTemplate(
            PlatformTransactionManager manager) {
        return transactionTemplate(manager);
    }

    @Override
    public String parametersDigest(
            String operation, String resourceType, Long resourceId, JsonNode parameters) {
        return digest(operation, resourceType, resourceId, parameters);
    }

    @Override
    @Transactional(readOnly = true)
    public ProposalView get(long userId, long approvalRequestId) {
        return view(require(userId, approvalRequestId));
    }

    @Override
    @Transactional
    public ProposalView propose(long userId, ProposalCommand command) {
        try {
            return proposeInternal(userId, command);
        } catch (RuntimeException exception) {
            if (auditService != null)
                auditService.recordFailure(
                        userId,
                        command == null || command.resourceType() == null
                                ? "approval_request"
                                : command.resourceType(),
                        command == null || command.resourceId() == null
                                ? null
                                : Long.toString(command.resourceId()),
                        "approval.propose",
                        "rejected",
                        errorCode(exception),
                        null,
                        command == null ? null : command.idempotencyKey() + ":propose",
                        Map.of("exception_type", exception.getClass().getSimpleName()));
            throw exception;
        }
    }

    private ProposalView proposeInternal(long userId, ProposalCommand command) {
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
        if (write.resourceId() != null) {
            List<ApprovalRequestRepository.ApprovalSnapshot> superseded =
                    store.findSupersedableForResource(
                            userId,
                            write.resourceType(),
                            write.resourceId(),
                            write.operation(),
                            write.approvalRequestId(),
                            Instant.now());
            store.markSupersededForResource(
                    userId,
                    write.resourceType(),
                    write.resourceId(),
                    write.operation(),
                    write.approvalRequestId(),
                    Instant.now());
            if (superseded != null)
                for (ApprovalRequestRepository.ApprovalSnapshot old : superseded)
                    audit(
                            old.approvalRequestId(),
                            userId,
                            trace(old),
                            "approval.superseded",
                            old.parametersDigest(),
                            old.idempotencyKey() + ":superseded");
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
        if ("confirmed".equals(approval.status())
                && !Instant.now().isBefore(approval.expiresAt())) {
            store.markExpired(userId, approvalRequestId, Instant.now());
            throw new BusinessException(ErrorCode.CONFLICT, "确认请求已过期");
        }
        if ("confirmed".equals(approval.status()) || "executed".equals(approval.status()))
            return view(approval);
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
    public ProposalView reject(long userId, long approvalRequestId, JsonNode parameters) {
        ApprovalRequestRepository.ApprovalSnapshot approval = require(userId, approvalRequestId);
        requireOperation(approval);
        requireDigest(approval, digestForExecution(approval, parameters));
        if ("rejected".equals(approval.status())) return view(approval);
        ensurePending(approval, userId);
        if (store.markRejected(userId, approvalRequestId, Instant.now()) != 1)
            throw new BusinessException(ErrorCode.CONFLICT, "confirmation state changed");
        audit(
                approvalRequestId,
                userId,
                trace(approval),
                "approval.reject",
                approval.parametersDigest(),
                approval.idempotencyKey() + ":reject");
        completeAgentWrite(approval, null, null, false);
        return view(require(userId, approvalRequestId));
    }

    @Override
    public ExecuteView execute(long userId, long approvalRequestId, JsonNode parameters) {
        return executeWithTransaction(userId, null, approvalRequestId, null, parameters);
    }

    @Override
    public ExecuteView executeForAgent(
            long userId,
            long agentRunId,
            long approvalRequestId,
            String idempotencyKey,
            JsonNode parameters) {
        return executeWithTransaction(
                userId, agentRunId, approvalRequestId, idempotencyKey, parameters);
    }

    private ExecuteView executeWithTransaction(
            long userId,
            Long expectedAgentRunId,
            long approvalRequestId,
            String expectedIdempotencyKey,
            JsonNode parameters) {
        boolean[] businessAttempted = {false};
        try {
            if (executionTransactions == null)
                return executeInternal(
                        userId,
                        expectedAgentRunId,
                        approvalRequestId,
                        expectedIdempotencyKey,
                        parameters,
                        businessAttempted);
            return executionTransactions.execute(
                    status ->
                            executeInternal(
                                    userId,
                                    expectedAgentRunId,
                                    approvalRequestId,
                                    expectedIdempotencyKey,
                                    parameters,
                                    businessAttempted));
        } catch (RuntimeException failure) {
            if (businessAttempted[0]) recordFailure(userId, approvalRequestId);
            throw failure;
        }
    }

    private ExecuteView executeInternal(
            long userId,
            Long expectedAgentRunId,
            long approvalRequestId,
            String expectedIdempotencyKey,
            JsonNode parameters,
            boolean[] businessAttempted) {
        ApprovalRequestRepository.ApprovalSnapshot approval = require(userId, approvalRequestId);
        if (expectedAgentRunId != null && !expectedAgentRunId.equals(approval.agentRunId()))
            throw new BusinessException(ErrorCode.FORBIDDEN, "确认请求不属于当前 AgentRun");
        if (expectedIdempotencyKey != null
                && !expectedIdempotencyKey.equals(approval.idempotencyKey()))
            throw toolConflict("工具幂等键与确认事实不一致", "TOOL_IDEMPOTENCY_CONFLICT");
        requireOperation(approval);
        requireDigest(approval, digestForExecution(approval, parameters));
        if ("executed".equals(approval.status())) {
            Long shoppingListId = null;
            if ("meal_plan".equals(approval.resourceType())
                    && approval.resourceId() != null
                    && plans != null) {
                MealPlanService.ShoppingListView shoppingList =
                        plans.shoppingList(userId, approval.resourceId());
                shoppingListId = shoppingList == null ? null : shoppingList.shoppingListId();
            }
            return new ExecuteView(
                    approval.approvalRequestId(),
                    approval.operation(),
                    approval.status(),
                    approval.resourceId(),
                    shoppingListId);
        }
        ensureExecutable(approval, userId);
        if (!"confirmed".equals(approval.status()))
            throw toolConflict("写操作尚未确认", "TOOL_CONFIRMATION_REQUIRED");
        if ("save_plan".equals(approval.operation()) && approval.resourceId() == null)
            requireMealPlanCandidate(parameters);
        if ("food_log".equals(approval.resourceType()) && foods == null)
            throw new BusinessException(
                    ErrorCode.INVALID_ARGUMENT, "food log service is unavailable");
        if ("meal_plan".equals(approval.resourceType()) && plans == null)
            throw new BusinessException(
                    ErrorCode.INVALID_ARGUMENT, "meal plan service is unavailable");
        if ("save_plan".equals(approval.operation()) && approval.resourceId() == null) {
            PlanValidator.Validation validation = PlanValidator.evaluate(parameters.get("plan"));
            if (!validation.valid())
                throw toolConflict("餐食计划未通过 Java 约束校验", "PLAN_CONSTRAINTS_UNSATISFIED");
        }
        if (store.markExecuted(userId, approvalRequestId, Instant.now()) != 1)
            throw new BusinessException(ErrorCode.CONFLICT, "确认请求执行状态已变化");
        businessAttempted[0] = true;
        // 状态占用和业务写入处于同一事务，避免并发执行重复调用业务写入。
        Long resourceId = approval.resourceId();
        Long secondaryResourceId = null;
        if ("save_plan".equals(approval.operation())) {
            if (resourceId == null) {
                JsonNode plan = parameters.get("plan");
                MealPlanService.PlanView created =
                        plans.create(
                                userId,
                                mealPlanCommand(
                                        approval.sessionId(),
                                        plan,
                                        businessPlanCreateKey(approval.idempotencyKey())));
                plans.validate(userId, created.mealPlanId());
                MealPlanService.PlanView saved =
                        plans.save(
                                userId,
                                created.mealPlanId(),
                                businessSaveKey(approval.idempotencyKey()));
                resourceId = saved == null ? created.mealPlanId() : saved.mealPlanId();
                if (store.updateExecutedResource(
                                userId, approvalRequestId, resourceId, Instant.now())
                        != 1)
                    throw new BusinessException(
                            ErrorCode.CONFLICT, "created meal plan could not be bound");
                MealPlanService.ShoppingListView shoppingList =
                        plans.shoppingList(userId, resourceId);
                if (shoppingList == null)
                    throw new BusinessException(ErrorCode.INTERNAL_ERROR, "购物清单写入后无法读取");
                secondaryResourceId = shoppingList.shoppingListId();
            } else {
                plans.save(userId, resourceId, businessSaveKey(approval.idempotencyKey()));
            }
        } else if ("create".equals(approval.operation())) {
            resourceId = createFoodLog(userId, approval, parameters);
            bindCreatedResource(userId, approvalRequestId, resourceId);
        } else if ("update".equals(approval.operation())) {
            foods.update(
                    userId,
                    requiredResourceId(approval),
                    revision(parameters),
                    new FoodLogService.UpdateCommand(
                            instant(parameters, "meal_time"),
                            mealType(parameters),
                            text(parameters, "notes"),
                            businessFoodLogKey("update", approval.idempotencyKey()),
                            foodItems(parameters)));
        } else if ("delete".equals(approval.operation())) {
            foods.delete(
                    userId,
                    requiredResourceId(approval),
                    revision(parameters),
                    businessFoodLogKey("delete", approval.idempotencyKey()));
        } else if ("restore".equals(approval.operation())) {
            foods.restore(
                    userId,
                    requiredResourceId(approval),
                    revision(parameters),
                    businessFoodLogKey("restore", approval.idempotencyKey()));
        }
        audit(
                approvalRequestId,
                userId,
                trace(approval),
                "approval.execute",
                approval.parametersDigest(),
                approval.idempotencyKey() + ":execute");
        completeAgentWrite(approval, resourceId, secondaryResourceId, true);
        return new ExecuteView(
                approvalRequestId,
                approval.operation(),
                "executed",
                resourceId,
                secondaryResourceId);
    }

    private void recordFailure(long userId, long approvalRequestId) {
        Runnable action =
                () -> {
                    ApprovalRequestRepository.ApprovalSnapshot approval =
                            store.findOwned(userId, approvalRequestId);
                    if (approval == null) return;
                    if (store.markFailed(userId, approvalRequestId, Instant.now()) == 1)
                        audit(
                                approvalRequestId,
                                userId,
                                trace(approval),
                                "approval.failed",
                                approval.parametersDigest(),
                                approval.idempotencyKey() + ":failed");
                };
        if (failureTransactions == null) {
            action.run();
        } else {
            failureTransactions.execute(
                    status -> {
                        action.run();
                        return null;
                    });
        }
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
                "food_log".equals(command.resourceType())
                        && Set.of("create", "update", "delete", "restore")
                                .contains(command.operation());
        if (!mealPlan && !foodLog)
            throw new BusinessException(
                    ErrorCode.INVALID_ARGUMENT,
                    "当前只支持 meal_plan.save_plan 或 food_log.create/update/delete/restore");
        if (mealPlan && command.resourceId() != null && command.resourceId() <= 0)
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "确认资源 ID 无效");
        if (mealPlan && command.resourceId() == null)
            requireMealPlanCandidate(command.parameters());
        if (foodLog && "create".equals(command.operation()) && command.resourceId() != null)
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "food_log.create 不应绑定已有资源");
        if (foodLog
                && !"create".equals(command.operation())
                && (command.resourceId() == null || command.resourceId() <= 0))
            throw new BusinessException(
                    ErrorCode.INVALID_ARGUMENT, "food log resource id is required");
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
                "food_log".equals(approval.resourceType())
                        && Set.of("create", "update", "delete", "restore")
                                .contains(approval.operation());
        if (!mealPlan && !foodLog)
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "当前不支持该确认写操作");
    }

    private void requireMealPlanCandidate(JsonNode parameters) {
        if (parameters == null || !parameters.path("plan").isObject())
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "餐食计划候选不能为空");
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

    private void ensureExecutable(
            ApprovalRequestRepository.ApprovalSnapshot approval, long userId) {
        if (("pending".equals(approval.status()) || "confirmed".equals(approval.status()))
                && !Instant.now().isBefore(approval.expiresAt())) {
            store.markExpired(userId, approval.approvalRequestId(), Instant.now());
            throw toolConflict("confirmation has expired", "TOOL_CONFIRMATION_EXPIRED");
        }
        if ("pending".equals(approval.status()))
            throw toolConflict("confirmation is required", "TOOL_CONFIRMATION_REQUIRED");
        if ("rejected".equals(approval.status()))
            throw toolConflict("confirmation was rejected", "TOOL_CONFIRMATION_REJECTED");
        if ("expired".equals(approval.status()))
            throw toolConflict("confirmation has expired", "TOOL_CONFIRMATION_EXPIRED");
        if ("failed".equals(approval.status()))
            throw toolConflict("previous execution failed", "TOOL_EXECUTION_FAILED");
        if ("superseded".equals(approval.status()))
            throw toolConflict("confirmation was superseded", "TOOL_CONFIRMATION_SUPERSEDED");
        if (!"confirmed".equals(approval.status()))
            throw toolConflict("confirmation state is not executable", "TOOL_POLICY_DENIED");
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
            JsonNode valueTree = mapper.valueToTree(Arrays.asList(values));
            String canonical = mapper.writeValueAsString(canonicalize(valueTree));
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(
                                            canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (JsonProcessingException | NoSuchAlgorithmException | IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "确认参数摘要计算失败");
        }
    }

    /** 参数摘要必须与 JSON 对象的字段插入顺序无关，但数组顺序仍然具有业务含义。 */
    private JsonNode canonicalize(JsonNode value) {
        if (value == null || value.isValueNode()) return value;
        if (value.isArray()) {
            ArrayNode result = mapper.createArrayNode();
            value.forEach(item -> result.add(canonicalize(item)));
            return result;
        }
        ObjectNode result = mapper.createObjectNode();
        TreeSet<String> fields = new TreeSet<>();
        value.fieldNames().forEachRemaining(fields::add);
        for (String field : fields) {
            result.set(field, canonicalize(value.get(field)));
        }
        return result;
    }

    private void audit(
            long id, long userId, TraceContext trace, String action, String digest, String key) {
        if (auditService == null) return;
        auditService.record(
                trace,
                userId,
                "approval_request",
                Long.toString(id),
                action,
                "success",
                null,
                digest,
                key,
                Map.of());
    }

    private void completeAgentWrite(
            ApprovalRequestRepository.ApprovalSnapshot approval,
            Long resourceId,
            Long secondaryResourceId,
            boolean written) {
        if (approval.agentRunId() == null || runtimeEvents == null) return;
        if ("meal_plan".equals(approval.resourceType())) {
            runtimeEvents.completeAgentWrite(
                    approval.agentRunId(),
                    approval.approvalRequestId(),
                    resourceId,
                    approval.requestId(),
                    approval.traceId(),
                    "meal_plan",
                    approval.operation(),
                    secondaryResourceId,
                    written);
            return;
        }
        runtimeEvents.completeAgentWrite(
                approval.agentRunId(),
                approval.approvalRequestId(),
                resourceId,
                approval.requestId(),
                approval.traceId(),
                written);
    }

    private static String errorCode(RuntimeException exception) {
        if (exception instanceof BusinessException business) return business.errorCode().code();
        if (exception instanceof com.foodmate.shared.runtime.RuntimeException runtime)
            return runtime.code();
        return "APPROVAL_PROPOSAL_FAILED";
    }

    private String businessSaveKey(String approvalKey) {
        return "plan_" + digest("meal_plan.save", approvalKey);
    }

    private String businessPlanCreateKey(String approvalKey) {
        return "plan_create_" + digest("meal_plan.create", approvalKey);
    }

    private String businessFoodLogKey(String operation, String approvalKey) {
        return "food_" + digest("food_log." + operation, approvalKey);
    }

    private long requiredResourceId(ApprovalRequestRepository.ApprovalSnapshot approval) {
        if (approval.resourceId() == null || approval.resourceId() <= 0)
            throw new BusinessException(
                    ErrorCode.INVALID_ARGUMENT, "food log resource id is required");
        return approval.resourceId();
    }

    private MealPlanService.CreateCommand mealPlanCommand(
            Long sessionId, JsonNode plan, String idempotencyKey) {
        return new MealPlanService.CreateCommand(
                sessionId,
                text(plan, "plan_name"),
                requiredInt(plan, "people"),
                requiredInt(plan, "days"),
                number(plan, "budget"),
                optionalInt(plan, "calorie_target"),
                optionalInt(plan, "protein_target"),
                strings(plan.get("allergens")),
                strings(plan.get("dislikes")),
                plan.get("days_plan"),
                idempotencyKey);
    }

    private static int requiredInt(JsonNode object, String field) {
        Integer value = optionalInt(object, field);
        if (value == null || value <= 0)
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "餐食计划 " + field + " 无效");
        return value;
    }

    private static Integer optionalInt(JsonNode object, String field) {
        JsonNode value = object == null ? null : object.get(field);
        if (value == null || value.isNull()) return null;
        if (!value.canConvertToInt())
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "餐食计划 " + field + " 无效");
        return value.asInt();
    }

    private static java.math.BigDecimal number(JsonNode object, String field) {
        JsonNode value = object == null ? null : object.get(field);
        if (value == null || value.isNull()) return null;
        if (!value.isNumber())
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "餐食计划 " + field + " 无效");
        return value.decimalValue();
    }

    private static List<String> strings(JsonNode value) {
        if (value == null || !value.isArray()) return List.of();
        List<String> result = new ArrayList<>();
        for (JsonNode item : value) {
            if (!item.isTextual() || item.asText().isBlank())
                throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "餐食计划约束无效");
            result.add(item.asText());
        }
        return List.copyOf(result);
    }

    private static long revision(JsonNode parameters) {
        JsonNode value = parameters == null ? null : parameters.get("revision");
        if (value == null || !value.canConvertToLong() || value.asLong() <= 0)
            throw new BusinessException(
                    ErrorCode.INVALID_ARGUMENT, "food log revision is required");
        return value.asLong();
    }

    private static MealType mealType(JsonNode parameters) {
        String value = text(parameters, "meal_type");
        if (value == null)
            throw new BusinessException(
                    ErrorCode.INVALID_ARGUMENT, "food log meal_type is required");
        try {
            return MealType.fromCode(value);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(
                    ErrorCode.INVALID_ARGUMENT, "food log meal_type is invalid");
        }
    }

    private long createFoodLog(
            long userId, ApprovalRequestRepository.ApprovalSnapshot approval, JsonNode parameters) {
        return foods.create(
                        userId,
                        new FoodLogService.CreateCommand(
                                approval.sessionId(),
                                approval.agentRunId(),
                                instant(parameters, "meal_time"),
                                mealType(parameters),
                                text(parameters, "notes"),
                                businessFoodLogKey("create", approval.idempotencyKey()),
                                "agent",
                                foodItems(parameters)))
                .foodLogId();
    }

    private void bindCreatedResource(long userId, long approvalRequestId, long resourceId) {
        if (store.updateExecutedResource(userId, approvalRequestId, resourceId, Instant.now()) != 1)
            throw new BusinessException(ErrorCode.CONFLICT, "created food log could not be bound");
    }

    private static Instant instant(JsonNode parameters, String field) {
        String value = text(parameters, field);
        if (value == null)
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "food_log.create 缺少 " + field);
        try {
            return Instant.parse(value);
        } catch (DateTimeParseException exception) {
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "food_log.create 的时间无效");
        }
    }

    private static String text(JsonNode parameters, String field) {
        JsonNode value = parameters.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static List<FoodLogService.ItemCommand> foodItems(JsonNode parameters) {
        if (!parameters.path("items").isArray())
            throw new BusinessException(ErrorCode.INVALID_ARGUMENT, "food_log.create 缺少 items");
        List<FoodLogService.ItemCommand> items = new ArrayList<>();
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
