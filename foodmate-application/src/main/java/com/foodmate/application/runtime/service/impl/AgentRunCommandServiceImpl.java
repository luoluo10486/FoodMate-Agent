package com.foodmate.application.runtime.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.foodmate.application.account.service.UserAccountService;
import com.foodmate.application.common.service.OperationAuditService;
import com.foodmate.application.conversation.service.SessionSummaryService;
import com.foodmate.application.runtime.admission.AgentAdmissionService;
import com.foodmate.application.runtime.command.AgentRunBudgetDefaults;
import com.foodmate.application.runtime.port.out.AgentRunCommandRepository;
import com.foodmate.application.runtime.port.out.ModelGovernanceRepository.ModelGovernanceSnapshot;
import com.foodmate.application.runtime.service.AgentRunCommandService;
import com.foodmate.application.runtime.service.ModelGovernanceService;
import com.foodmate.shared.conversation.enums.MessageRole;
import com.foodmate.shared.error.ErrorCode;
import com.foodmate.shared.id.IdGenerator;
import com.foodmate.shared.runtime.V1RunCommand;
import com.foodmate.shared.runtime.V1RunSupersededEvent;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 在网络调用前持久化 AgentRun 和不可变 dispatch payload。 */
@Service
public class AgentRunCommandServiceImpl implements AgentRunCommandService {
    private final AgentRunCommandRepository store;
    private final IdGenerator ids;
    private final UserAccountService accounts;
    private final AgentRunBudgetDefaults budgetDefaults;
    private final AgentAdmissionService admission;
    private final SessionSummaryService summaries;
    private final ModelGovernanceService modelGovernance;
    private final ObjectMapper mapper;
    private final OperationAuditService audit;

    public AgentRunCommandServiceImpl(
            ObjectProvider<AgentRunCommandRepository> store,
            IdGenerator ids,
            UserAccountService accounts,
            AgentRunBudgetDefaults budgetDefaults,
            AgentAdmissionService admission,
            SessionSummaryService summaries) {
        this(store, ids, accounts, budgetDefaults, admission, summaries, null, null);
    }

    @Autowired
    public AgentRunCommandServiceImpl(
            ObjectProvider<AgentRunCommandRepository> store,
            IdGenerator ids,
            UserAccountService accounts,
            AgentRunBudgetDefaults budgetDefaults,
            AgentAdmissionService admission,
            SessionSummaryService summaries,
            ObjectProvider<OperationAuditService> auditProvider) {
        this(store, ids, accounts, budgetDefaults, admission, summaries, auditProvider, null);
    }

    public AgentRunCommandServiceImpl(
            ObjectProvider<AgentRunCommandRepository> store,
            IdGenerator ids,
            UserAccountService accounts,
            AgentRunBudgetDefaults budgetDefaults,
            AgentAdmissionService admission,
            SessionSummaryService summaries,
            ObjectProvider<OperationAuditService> auditProvider,
            ObjectProvider<ModelGovernanceService> modelGovernanceProvider) {
        this.store = store.getIfAvailable();
        this.ids = ids;
        this.accounts = accounts;
        this.budgetDefaults = budgetDefaults;
        this.admission = admission;
        this.summaries = summaries;
        this.audit = auditProvider == null ? null : auditProvider.getIfAvailable();
        this.modelGovernance =
                modelGovernanceProvider == null ? null : modelGovernanceProvider.getIfAvailable();
        this.mapper =
                new ObjectMapper()
                        .findAndRegisterModules()
                        .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
                        .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    @Transactional
    @Override
    public UserAccountService.MessageRecord createUserMessageRun(
            long userId, long sessionId, String content, String traceId) {
        return createUserMessageRunDetails(userId, sessionId, content, traceId).message();
    }

    /**
     * Creates the persisted V1 run and returns the identifiers needed by an HTTP caller.
     *
     * <p>The old method intentionally keeps returning only the message record for callers that only
     * persist messages. The V1 HTTP entry point must also expose the dispatch identifier so the
     * caller can subscribe to the matching durable SSE stream.
     */
    @Transactional
    @Override
    public RunCreation createUserMessageRunDetails(
            long userId, long sessionId, String content, String traceId) {
        Long runId = null;
        try {
            runId = ids.nextId();
            String runIdText = Long.toString(runId);
            if (store == null) {
                RunCreation result =
                        new RunCreation(
                                accounts.addMessage(
                                        userId,
                                        sessionId,
                                        MessageRole.USER.code(),
                                        content,
                                        null,
                                        runId),
                                runIdText,
                                null,
                                "persisted");
                record(
                        userId,
                        "agent_run",
                        runIdText,
                        "agent_run.create",
                        "success",
                        Map.of("session_id", sessionId));
                return result;
            }
            // waiting_user 的旧 Run 由本次补充消息接续：新 Run 记 parent，旧 Run 迁移为 superseded 终态。
            accounts.listMessages(userId, sessionId, 1, 1);
            Long parentRunId = store.waitingRun(sessionId);

            // 消息外键依赖 agent_runs，因此先建运行记录，再保存消息并回填 user_message_id。
            store.insertRun(runId, sessionId, traceId, userId, parentRunId);
            UserAccountService.MessageRecord message =
                    accounts.addMessage(
                            userId, sessionId, MessageRole.USER.code(), content, null, runId);
            // 超过 8 条有效原始消息后更新摘要；摘要不是消息权威，只是下一次 Context 的压缩来源。
            summaries.maybeRefresh(userId, sessionId);

            store.bindMessage(runId, message.messageId());
            if (parentRunId != null) {
                supersedeParentRun(parentRunId, runId, userId);
            }
            ModelGovernanceSnapshot governanceSnapshot =
                    modelGovernance == null ? null : modelGovernance.resolve("agent_run", "chat");
            insertInitialBudgetSnapshot(runId, governanceSnapshot);

            // command、摘要和 outbox 必须在同一事务里生成，publisher 提交后才允许发送。
            String dispatchId = "dsp_" + UUID.randomUUID().toString().replace("-", "");
            String requestId = "req_" + UUID.randomUUID().toString().replace("-", "");
            // 请求级 deadline 至少覆盖固化的执行预算，排队时间由独立 queue timeout 处理。
            Instant deadline = Instant.now().plusSeconds(budgetDefaults.executionTimeoutSeconds());
            List<V1RunCommand.RecentMessage> recentMessages =
                    store.recentMessages(sessionId).reversed().stream()
                            .map(
                                    row ->
                                            new V1RunCommand.RecentMessage(
                                                    row.messageId(),
                                                    row.role(),
                                                    row.content(),
                                                    row.sequenceNo()))
                            .toList();
            // Context 只装配授权后的摘要和长期记忆；Python 不直接查询 FoodMate 数据库。
            AgentRunCommandRepository.SummarySnapshot summary = store.summary(sessionId);
            V1RunCommand.SessionSummary sessionSummary =
                    summary == null
                            ? null
                            : new V1RunCommand.SessionSummary(
                                    summary.summaryId(),
                                    summary.summaryText(),
                                    summary.keyConstraints(),
                                    summary.coveredFromSequence(),
                                    summary.coveredToSequence(),
                                    summary.sourceMessageCount(),
                                    summary.promptVersion(),
                                    summary.contentDigest(),
                                    summary.version());
            List<V1RunCommand.MemoryContext> longTermMemories =
                    store.memories(userId).stream()
                            .map(
                                    memory ->
                                            new V1RunCommand.MemoryContext(
                                                    memory.memoryId(),
                                                    memory.memoryType(),
                                                    memory.memoryKey(),
                                                    memory.memoryValue(),
                                                    memory.confidence(),
                                                    memory.scope()))
                            .toList();
            // 最近消息已经作为授权上下文随命令发送。不要再为同一份上下文创建 SQL
            // Proposal：messages 不属于 database_query 的业务 Catalog，且 Python 不应为上下文读取
            // 触发额外的 Java Tool Gateway 调用。
            V1RunCommand.AuthorizedContext authorizedContext =
                    new V1RunCommand.AuthorizedContext(
                            Long.toString(sessionId),
                            "Asia/Shanghai",
                            "zh-CN",
                            "v1",
                            recentMessages,
                            sessionSummary,
                            longTermMemories,
                            null,
                            "public_published");
            int maxTotalTokens =
                    governanceSnapshot == null
                            ? budgetDefaults.maxTotalTokens()
                            : governanceSnapshot.maxTotalTokens();
            java.math.BigDecimal maxCostCny =
                    governanceSnapshot == null
                            ? budgetDefaults.maxCostCny()
                            : governanceSnapshot.maxCostCny();
            int maxStepRetries =
                    governanceSnapshot == null
                            ? budgetDefaults.maxStepRetries()
                            : governanceSnapshot.maxStepRetries();
            int maxModelCalls =
                    governanceSnapshot == null
                            ? budgetDefaults.maxModelCalls()
                            : governanceSnapshot.maxModelCalls();
            V1RunCommand.BudgetSnapshot budgetSnapshot =
                    new V1RunCommand.BudgetSnapshot(
                            maxTotalTokens,
                            maxCostCny,
                            maxStepRetries,
                            budgetDefaults.maxReplans(),
                            budgetDefaults.maxAnswerRewrites(),
                            budgetDefaults.maxTotalSteps(),
                            maxModelCalls,
                            budgetDefaults.queueTimeoutSeconds(),
                            budgetDefaults.executionTimeoutSeconds(),
                            budgetDefaults.nodeTimeoutSeconds(),
                            budgetDefaults.waitingUserTimeoutSeconds(),
                            1,
                            governanceSnapshot == null
                                    ? budgetDefaults.configVersion()
                                    : governanceSnapshot.budgetPolicyVersion());
            V1RunCommand.ModelSnapshot modelSnapshot =
                    governanceSnapshot == null
                            ? null
                            : new V1RunCommand.ModelSnapshot(
                                    governanceSnapshot.scene(),
                                    governanceSnapshot.modelType(),
                                    governanceSnapshot.routeVersion(),
                                    governanceSnapshot.providerCode(),
                                    governanceSnapshot.modelName(),
                                    governanceSnapshot.fallbackProviderCode(),
                                    governanceSnapshot.fallbackModelName(),
                                    governanceSnapshot.priceVersion(),
                                    governanceSnapshot.inputPricePerMillion(),
                                    governanceSnapshot.outputPricePerMillion(),
                                    governanceSnapshot.budgetPolicyVersion(),
                                    governanceSnapshot.modelTimeoutMs());
            V1RunCommand.RuntimeOptions runtimeOptions =
                    new V1RunCommand.RuntimeOptions(
                            "foodmate-m1-4-deterministic-v1",
                            budgetDefaults.maxTotalSteps(),
                            true,
                            budgetSnapshot,
                            modelSnapshot);
            // 将接受 Run 时的不可变预算快照随命令发送，Python 恢复时不得读取新环境变量覆盖它。
            V1RunCommand.V1Message commandMessage =
                    new V1RunCommand.V1Message(
                            Long.toString(message.messageId()), content, List.of());
            String requestHash =
                    digest(
                            new V1RunCommand.RequestHashInput(
                                    "v1",
                                    runIdText,
                                    dispatchId,
                                    1,
                                    deadline,
                                    commandMessage,
                                    authorizedContext,
                                    runtimeOptions));
            V1RunCommand command =
                    new V1RunCommand(
                            "v1",
                            runIdText,
                            dispatchId,
                            1,
                            requestId,
                            traceId,
                            requestHash,
                            deadline,
                            commandMessage,
                            authorizedContext,
                            runtimeOptions,
                            null);
            String payload = json(command);
            long dispatchRowId = ids.nextId();
            String fence = "fence_" + UUID.randomUUID().toString().replace("-", "");
            store.insertDispatch(dispatchRowId, runId, dispatchId, fence, deadline);
            store.insertOutbox(
                    ids.nextId(), dispatchRowId, runId, dispatchId, deadline, payload, requestHash);
            // Redis 准入结果必须回写 Outbox：queued 状态不能被 Relay 当作 pending 发送。
            int queuePriority = parentRunId == null ? 0 : 10;
            AgentAdmissionService.Admission admissionResult =
                    admission.admit(runIdText, userId, sessionId, queuePriority);
            if (admissionResult.state() == AgentAdmissionService.State.QUEUED) {
                store.queueOutbox(runId, dispatchId, queuePriority);
            }
            store.activateDispatch(runId, dispatchRowId);
            RunCreation result =
                    new RunCreation(
                            message,
                            runIdText,
                            dispatchId,
                            admissionResult.state().name().toLowerCase(java.util.Locale.ROOT));
            record(
                    userId,
                    "agent_run",
                    runIdText,
                    "agent_run.create",
                    "success",
                    Map.of("session_id", sessionId, "dispatch_status", result.status()));
            return result;
        } catch (RuntimeException exception) {
            failure(
                    userId,
                    "agent_run",
                    runId == null ? null : Long.toString(runId),
                    "agent_run.create",
                    exception,
                    Map.of("session_id", sessionId));
            throw exception;
        }
    }

    private void supersedeParentRun(long parentRunId, long continuationRunId, long userId) {
        try {
            // 旧 Run 迁移到 superseded 终态；迟到事件因 dispatch 不再 active 而被拒绝。
            int updated = store.supersede(parentRunId, continuationRunId);
            if (updated == 0) {
                throw new com.foodmate.shared.runtime.RuntimeException(
                        "RUNTIME_STATE_CONFLICT", "parent run is no longer waiting for user input");
            }
            store.supersedeDispatch(parentRunId);
            store.expireOutbox(parentRunId);
            // 旧 Run 的 SSE 订阅方通过 run.superseded 终态事件结束等待。
            long streamSeq = store.lockNextSseSequence(parentRunId);
            store.insertSse(
                    ids.nextId(),
                    parentRunId,
                    "sse_" + ids.nextId(),
                    streamSeq,
                    parentRunId + ":superseded:" + continuationRunId,
                    json(new V1RunSupersededEvent(Long.toString(continuationRunId))));
            store.updateSseSequence(parentRunId, streamSeq);
            record(
                    userId,
                    "agent_run",
                    Long.toString(parentRunId),
                    "agent_run.superseded",
                    "success",
                    Map.of("continuation_run_id", continuationRunId));
        } catch (RuntimeException exception) {
            failure(
                    userId,
                    "agent_run",
                    Long.toString(parentRunId),
                    "agent_run.superseded",
                    exception,
                    Map.of("continuation_run_id", continuationRunId));
            throw exception;
        }
    }

    private void record(
            long userId,
            String targetType,
            String targetId,
            String action,
            String result,
            Map<String, ?> metadata) {
        if (audit != null)
            audit.record(userId, targetType, targetId, action, result, null, null, null, metadata);
    }

    private void failure(
            long userId,
            String targetType,
            String targetId,
            String action,
            RuntimeException exception,
            Map<String, ?> metadata) {
        if (audit != null)
            audit.recordFailure(
                    userId,
                    targetType,
                    targetId,
                    action,
                    "failed",
                    errorCode(exception),
                    null,
                    null,
                    metadata);
    }

    private static String errorCode(RuntimeException exception) {
        if (exception instanceof com.foodmate.shared.runtime.RuntimeException runtime)
            return runtime.code();
        if (exception instanceof IllegalArgumentException) return ErrorCode.INVALID_ARGUMENT.code();
        return ErrorCode.INTERNAL_ERROR.code();
    }

    private void insertInitialBudgetSnapshot(
            long runId, ModelGovernanceSnapshot governanceSnapshot) {
        int maxTotalTokens =
                governanceSnapshot == null
                        ? budgetDefaults.maxTotalTokens()
                        : governanceSnapshot.maxTotalTokens();
        java.math.BigDecimal maxCostCny =
                governanceSnapshot == null
                        ? budgetDefaults.maxCostCny()
                        : governanceSnapshot.maxCostCny();
        int maxStepRetries =
                governanceSnapshot == null
                        ? budgetDefaults.maxStepRetries()
                        : governanceSnapshot.maxStepRetries();
        int maxModelCalls =
                governanceSnapshot == null
                        ? budgetDefaults.maxModelCalls()
                        : governanceSnapshot.maxModelCalls();
        store.insertBudget(
                ids.nextId(),
                runId,
                maxTotalTokens,
                maxCostCny,
                maxStepRetries,
                budgetDefaults.maxReplans(),
                budgetDefaults.maxAnswerRewrites(),
                budgetDefaults.maxTotalSteps(),
                maxModelCalls,
                budgetDefaults.queueTimeoutSeconds(),
                budgetDefaults.executionTimeoutSeconds(),
                budgetDefaults.nodeTimeoutSeconds(),
                budgetDefaults.waitingUserTimeoutSeconds(),
                governanceSnapshot == null
                        ? budgetDefaults.configVersion()
                        : governanceSnapshot.budgetPolicyVersion());
    }

    private String json(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("runtime command is not JSON", exception);
        }
    }

    private String digest(Object value) {
        try {
            byte[] bytes = json(value).getBytes(StandardCharsets.UTF_8);
            return "sha256:"
                    + HexFormatHolder.encode(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static final class HexFormatHolder {
        private static String encode(byte[] bytes) {
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte value : bytes) result.append(String.format("%02x", value));
            return result.toString();
        }
    }
}
