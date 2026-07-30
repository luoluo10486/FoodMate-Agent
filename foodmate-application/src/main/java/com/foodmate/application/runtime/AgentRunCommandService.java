package com.foodmate.application.runtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.foodmate.application.account.UserAccountService;
import com.foodmate.application.runtime.persistence.AgentRunCommandStore;
import com.foodmate.shared.id.IdGenerator;
import com.foodmate.shared.runtime.V1RunCommand;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 在网络调用前持久化 AgentRun 和不可变 dispatch payload。 */
@Service
public class AgentRunCommandService {
    private final AgentRunCommandStore store;
    private final IdGenerator ids;
    private final UserAccountService accounts;
    private final AgentRunBudgetDefaults budgetDefaults;
    private final AgentAdmissionService admission;
    private final SessionSummaryService summaries;
    private final ObjectMapper mapper;

    public AgentRunCommandService(
            ObjectProvider<AgentRunCommandStore> store,
            IdGenerator ids,
            UserAccountService accounts,
            AgentRunBudgetDefaults budgetDefaults,
            AgentAdmissionService admission,
            SessionSummaryService summaries) {
        this.store = store.getIfAvailable();
        this.ids = ids;
        this.accounts = accounts;
        this.budgetDefaults = budgetDefaults;
        this.admission = admission;
        this.summaries = summaries;
        this.mapper =
                new ObjectMapper()
                        .findAndRegisterModules()
                        .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
                        .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    @Transactional
    public UserAccountService.MessageRecord createUserMessageRun(
            long userId, long sessionId, String content, String traceId) {
        long runId = ids.nextId();
        String runIdText = Long.toString(runId);
        if (store == null)
            return accounts.addMessage(userId, sessionId, "user", content, null, runId);
        // waiting_user 的旧 Run 由本次补充消息接续：新 Run 记 parent，旧 Run 迁移为 superseded 终态。
        accounts.listMessages(userId, sessionId, 1, 1);
        Long parentRunId = store.waitingRun(sessionId);

        // 消息外键依赖 agent_runs，因此先建运行记录，再保存消息并回填 user_message_id。
        store.insertRun(runId, sessionId, traceId, userId, parentRunId);
        UserAccountService.MessageRecord message =
                accounts.addMessage(userId, sessionId, "user", content, null, runId);
        // 超过 8 条有效原始消息后更新摘要；摘要不是消息权威，只是下一次 Context 的压缩来源。
        summaries.maybeRefresh(userId, sessionId);

        store.bindMessage(runId, message.messageId());
        if (parentRunId != null) {
            supersedeParentRun(parentRunId, runId);
        }
        insertInitialBudgetSnapshot(runId);

        // command、摘要和 outbox 必须在同一事务里生成，publisher 提交后才允许发送。
        String dispatchId = "dsp_" + UUID.randomUUID().toString().replace("-", "");
        String requestId = "req_" + UUID.randomUUID().toString().replace("-", "");
        // 请求级 deadline 至少覆盖固化的执行预算，排队时间由独立 queue timeout 处理。
        Instant deadline = Instant.now().plusSeconds(budgetDefaults.executionTimeoutSeconds());
        Map<String, Object> authorizedContext = new LinkedHashMap<>();
        authorizedContext.put("session_id", Long.toString(sessionId));
        authorizedContext.put("timezone", "Asia/Shanghai");
        authorizedContext.put("locale", "zh-CN");
        authorizedContext.put("tool_contract_version", "v1");
        authorizedContext.put("recent_messages", store.recentMessages(sessionId).reversed());
        // Context 只装配授权后的摘要和长期记忆；Python 不直接查询 FoodMate 数据库。
        Map<String, Object> summary = store.summary(sessionId);
        if (summary != null) authorizedContext.put("session_summary", summary);
        authorizedContext.put("long_term_memories", store.memories(userId));
        Map<String, Object> runtimeOptions = new LinkedHashMap<>();
        runtimeOptions.put("prompt_set_version", "foodmate-m1-4-deterministic-v1");
        runtimeOptions.put("max_steps", budgetDefaults.maxTotalSteps());
        runtimeOptions.put("stream_answer", true);
        // 将接受 Run 时的不可变预算快照随命令发送，Python 恢复时不得读取新环境变量覆盖它。
        Map<String, Object> budgetSnapshot = new LinkedHashMap<>();
        budgetSnapshot.put("max_total_tokens", budgetDefaults.maxTotalTokens());
        budgetSnapshot.put("max_cost_cny", budgetDefaults.maxCostCny());
        budgetSnapshot.put("max_step_retries", budgetDefaults.maxStepRetries());
        budgetSnapshot.put("max_replans", budgetDefaults.maxReplans());
        budgetSnapshot.put("max_answer_rewrites", budgetDefaults.maxAnswerRewrites());
        budgetSnapshot.put("max_total_steps", budgetDefaults.maxTotalSteps());
        budgetSnapshot.put("max_model_calls", budgetDefaults.maxModelCalls());
        budgetSnapshot.put("queue_timeout_seconds", budgetDefaults.queueTimeoutSeconds());
        budgetSnapshot.put("execution_timeout_seconds", budgetDefaults.executionTimeoutSeconds());
        budgetSnapshot.put("node_timeout_seconds", budgetDefaults.nodeTimeoutSeconds());
        budgetSnapshot.put(
                "waiting_user_timeout_seconds", budgetDefaults.waitingUserTimeoutSeconds());
        budgetSnapshot.put("revision", 1);
        budgetSnapshot.put("config_version", budgetDefaults.configVersion());
        runtimeOptions.put("budget_snapshot", budgetSnapshot);
        V1RunCommand.V1Message commandMessage =
                new V1RunCommand.V1Message(Long.toString(message.messageId()), content, List.of());
        String requestHash =
                digest(
                        Map.of(
                                "schema_version", "v1",
                                "run_id", runIdText,
                                "dispatch_id", dispatchId,
                                "attempt", 1,
                                "deadline_at", deadline,
                                "message", commandMessage,
                                "authorized_context", authorizedContext,
                                "runtime_options", runtimeOptions));
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
                        runtimeOptions);
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
        return message;
    }

    private void supersedeParentRun(long parentRunId, long continuationRunId) {
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
                json(Map.of("superseded_by_run_id", Long.toString(continuationRunId))));
        store.updateSseSequence(parentRunId, streamSeq);
    }

    private void insertInitialBudgetSnapshot(long runId) {
        store.insertBudget(
                ids.nextId(),
                runId,
                budgetDefaults.maxTotalTokens(),
                budgetDefaults.maxCostCny(),
                budgetDefaults.maxStepRetries(),
                budgetDefaults.maxReplans(),
                budgetDefaults.maxAnswerRewrites(),
                budgetDefaults.maxTotalSteps(),
                budgetDefaults.maxModelCalls(),
                budgetDefaults.queueTimeoutSeconds(),
                budgetDefaults.executionTimeoutSeconds(),
                budgetDefaults.nodeTimeoutSeconds(),
                budgetDefaults.waitingUserTimeoutSeconds(),
                budgetDefaults.configVersion());
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
