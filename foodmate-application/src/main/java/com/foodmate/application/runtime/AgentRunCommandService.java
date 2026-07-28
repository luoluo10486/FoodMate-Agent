package com.foodmate.application.runtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.foodmate.application.account.UserAccountService;
import com.foodmate.shared.id.IdGenerator;
import com.foodmate.shared.runtime.V1RunCommand;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 在网络调用前持久化 AgentRun 和不可变 dispatch payload。 */
@Service
public class AgentRunCommandService {
    private final JdbcTemplate jdbc;
    private final IdGenerator ids;
    private final UserAccountService accounts;
    private final AgentRunBudgetDefaults budgetDefaults;
    private final AgentAdmissionService admission;
    private final SessionSummaryService summaries;
    private final ObjectMapper mapper;

    public AgentRunCommandService(ObjectProvider<JdbcTemplate> jdbcProvider,
                                   IdGenerator ids,
                                   UserAccountService accounts,
                                   AgentRunBudgetDefaults budgetDefaults,
                                   AgentAdmissionService admission,
                                   SessionSummaryService summaries) {
        this.jdbc = jdbcProvider.getIfAvailable();
        this.ids = ids;
        this.accounts = accounts;
        this.budgetDefaults = budgetDefaults;
        this.admission = admission;
        this.summaries = summaries;
        this.mapper = new ObjectMapper().findAndRegisterModules()
                .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    @Transactional
    public UserAccountService.MessageRecord createUserMessageRun(long userId, long sessionId, String content, String traceId) {
        long runId = ids.nextId();
        String runIdText = Long.toString(runId);
        if (jdbc == null) {
            return accounts.addMessage(userId, sessionId, "user", content, null, runId);
        }

        // waiting_user 的旧 Run 由本次补充消息接续：新 Run 记 parent，旧 Run 迁移为 superseded 终态。
        accounts.listMessages(userId, sessionId, 1, 1);
        Long parentRunId = jdbc.query(
                "SELECT agent_run_id FROM agent_runs WHERE session_id=? AND status='waiting_user' AND is_deleted=FALSE ORDER BY created_at DESC LIMIT 1",
                (rs, row) -> rs.getLong(1), sessionId).stream().findFirst().orElse(null);

        // 消息外键依赖 agent_runs，因此先建运行记录，再保存消息并回填 user_message_id。
        if (parentRunId == null) {
            jdbc.update("INSERT INTO agent_runs(agent_run_id,session_id,status,trace_id,created_by) VALUES (?,?,?,?,?)",
                    runId, sessionId, "queued", traceId, userId);
        } else {
            jdbc.update("INSERT INTO agent_runs(agent_run_id,session_id,status,trace_id,created_by,parent_run_id,continuation_reason) VALUES (?,?,?,?,?,?,?)",
                    runId, sessionId, "queued", traceId, userId, parentRunId, "clarification");
        }
        UserAccountService.MessageRecord message = accounts.addMessage(userId, sessionId, "user", content, null, runId);
        // 超过 8 条有效原始消息后更新摘要；摘要不是消息权威，只是下一次 Context 的压缩来源。
        summaries.maybeRefresh(userId, sessionId);

        jdbc.update("UPDATE agent_runs SET user_message_id=?,updated_at=CURRENT_TIMESTAMP WHERE agent_run_id=?",
                message.messageId(), runId);
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
        authorizedContext.put("recent_messages", jdbc.query("SELECT message_id,role,content,sequence_no FROM messages WHERE session_id=? AND is_deleted=FALSE ORDER BY sequence_no DESC LIMIT 8",
                (rs, row) -> {
                    Map<String, Object> messageContext = new LinkedHashMap<>();
                    messageContext.put("message_id", Long.toString(rs.getLong(1)));
                    messageContext.put("role", rs.getString(2)); messageContext.put("content", rs.getString(3));
                    messageContext.put("sequence_no", rs.getInt(4)); return messageContext;
                }, sessionId).reversed());
        // Context 只装配授权后的摘要和长期记忆；Python 不直接查询 FoodMate 数据库。
        jdbc.query("SELECT summary_id,summary_text,key_constraints::text,covered_from_sequence,covered_to_sequence,source_message_count,prompt_version,content_digest,version FROM session_summaries WHERE session_id=? AND is_deleted=FALSE AND invalidated_at IS NULL",
                rs -> {
                    if (rs.next()) {
                        Map<String, Object> summary = new LinkedHashMap<>();
                        summary.put("summary_id", Long.toString(rs.getLong(1))); summary.put("summary_text", rs.getString(2));
                        summary.put("key_constraints", rs.getString(3)); summary.put("covered_from_sequence", rs.getInt(4));
                        summary.put("covered_to_sequence", rs.getInt(5)); summary.put("source_message_count", rs.getInt(6));
                        summary.put("prompt_version", rs.getString(7)); summary.put("content_digest", rs.getString(8)); summary.put("version", rs.getInt(9));
                        authorizedContext.put("session_summary", summary);
                    }
                    return null;
                }, sessionId);
        authorizedContext.put("long_term_memories", jdbc.query("SELECT memory_id,memory_type,memory_key,memory_value::text,confidence,scope,expires_at FROM user_memories WHERE user_id=? AND is_deleted=FALSE AND confirmation_status='confirmed' AND (expires_at IS NULL OR expires_at>CURRENT_TIMESTAMP) ORDER BY updated_at DESC LIMIT 20",
                (rs, row) -> {
                    Map<String, Object> memory = new LinkedHashMap<>();
                    memory.put("memory_id", Long.toString(rs.getLong(1))); memory.put("memory_type", rs.getString(2));
                    memory.put("memory_key", rs.getString(3)); memory.put("memory_value", rs.getString(4));
                    memory.put("confidence", rs.getBigDecimal(5)); memory.put("scope", rs.getString(6));
                    return memory;
                }, userId));
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
        budgetSnapshot.put("waiting_user_timeout_seconds", budgetDefaults.waitingUserTimeoutSeconds());
        budgetSnapshot.put("revision", 1);
        budgetSnapshot.put("config_version", budgetDefaults.configVersion());
        runtimeOptions.put("budget_snapshot", budgetSnapshot);
        V1RunCommand.V1Message commandMessage = new V1RunCommand.V1Message(Long.toString(message.messageId()), content, List.of());
        String requestHash = digest(Map.of(
                "schema_version", "v1",
                "run_id", runIdText,
                "dispatch_id", dispatchId,
                "attempt", 1,
                "deadline_at", deadline,
                "message", commandMessage,
                "authorized_context", authorizedContext,
                "runtime_options", runtimeOptions));
        V1RunCommand command = new V1RunCommand("v1", runIdText, dispatchId, 1, requestId, traceId,
                requestHash, deadline, commandMessage, authorizedContext, runtimeOptions);
        String payload = json(command);
        long dispatchRowId = ids.nextId();
        String fence = "fence_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.update("INSERT INTO agent_run_dispatches(agent_run_dispatch_id,agent_run_id,dispatch_id,attempt,active_epoch,fencing_token,admission_epoch,deadline_at) VALUES (?,?,?,?,?,?,?,?)",
                dispatchRowId, runId, dispatchId, 1, 1, fence, 0, java.sql.Timestamp.from(deadline));
        jdbc.update("INSERT INTO runtime_dispatch_outbox(outbox_id,agent_run_dispatch_id,agent_run_id,dispatch_id,run_id,attempt,schema_version,deadline_at,fencing_epoch,payload_json,request_hash) VALUES (?,?,?,?,?,?,?,?,?,CAST(? AS jsonb),?)",
                ids.nextId(), dispatchRowId, runId, dispatchId, runIdText, 1, "v1", java.sql.Timestamp.from(deadline), 1, payload, requestHash);
        // Redis 准入结果必须回写 Outbox：queued 状态不能被 Relay 当作 pending 发送。
        int queuePriority = parentRunId == null ? 0 : 10;
        AgentAdmissionService.Admission admissionResult = admission.admit(runIdText, userId, sessionId, queuePriority);
        if (admissionResult.state() == AgentAdmissionService.State.QUEUED) {
            jdbc.update("UPDATE runtime_dispatch_outbox SET status='queued',queued_at=CURRENT_TIMESTAMP,queue_priority=?,updated_at=CURRENT_TIMESTAMP WHERE agent_run_id=? AND dispatch_id=?",
                    queuePriority, runId, dispatchId);
        }
        jdbc.update("UPDATE agent_runs SET active_dispatch_id=?,updated_at=CURRENT_TIMESTAMP WHERE agent_run_id=?", dispatchRowId, runId);
        return message;
    }

    private void supersedeParentRun(long parentRunId, long continuationRunId) {
        // 旧 Run 迁移到 superseded 终态；迟到事件因 dispatch 不再 active 而被拒绝。
        int updated = jdbc.update(
                "UPDATE agent_runs SET status='superseded',superseded_by_run_id=?,admission_state='closed',updated_at=CURRENT_TIMESTAMP WHERE agent_run_id=? AND status='waiting_user'",
                continuationRunId, parentRunId);
        if (updated == 0) {
            throw new com.foodmate.shared.runtime.RuntimeException("RUNTIME_STATE_CONFLICT", "parent run is no longer waiting for user input");
        }
        jdbc.update("UPDATE agent_run_dispatches SET dispatch_arbitration_state='superseded',updated_at=CURRENT_TIMESTAMP WHERE agent_run_id=? AND dispatch_arbitration_state='active'", parentRunId);
        jdbc.update("UPDATE runtime_dispatch_outbox SET status='expired',updated_at=CURRENT_TIMESTAMP WHERE agent_run_id=? AND status='pending'", parentRunId);
        // 旧 Run 的 SSE 订阅方通过 run.superseded 终态事件结束等待。
        long streamSeq = jdbc.queryForObject("SELECT sse_last_stream_seq + 1 FROM agent_runs WHERE agent_run_id=? FOR UPDATE", Long.class, parentRunId);
        jdbc.update("INSERT INTO agent_run_sse_outbox(agent_run_sse_outbox_id,agent_run_id,sse_event_id,stream_seq,source_event_key,event_type,payload_json) VALUES (?,?,?,?,?,?,CAST(? AS jsonb))",
                ids.nextId(), parentRunId, "sse_" + ids.nextId(), streamSeq, parentRunId + ":superseded:" + continuationRunId, "run.superseded",
                json(Map.of("superseded_by_run_id", Long.toString(continuationRunId))));
        jdbc.update("UPDATE agent_runs SET sse_last_stream_seq=? WHERE agent_run_id=?", streamSeq, parentRunId);
    }

    private void insertInitialBudgetSnapshot(long runId) {
        jdbc.update("INSERT INTO agent_run_budget_snapshots(budget_snapshot_id,agent_run_id,revision,source,max_total_tokens,max_cost_cny,max_step_retries,max_replans,max_answer_rewrites,max_total_steps,max_model_calls,queue_timeout_seconds,execution_timeout_seconds,node_timeout_seconds,waiting_user_timeout_seconds,config_version) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                ids.nextId(), runId, 1, "initial",
                budgetDefaults.maxTotalTokens(), budgetDefaults.maxCostCny(),
                budgetDefaults.maxStepRetries(), budgetDefaults.maxReplans(), budgetDefaults.maxAnswerRewrites(),
                budgetDefaults.maxTotalSteps(), budgetDefaults.maxModelCalls(),
                budgetDefaults.queueTimeoutSeconds(), budgetDefaults.executionTimeoutSeconds(),
                budgetDefaults.nodeTimeoutSeconds(), budgetDefaults.waitingUserTimeoutSeconds(),
                budgetDefaults.configVersion());
    }

    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException exception) { throw new IllegalStateException("runtime command is not JSON", exception); }
    }

    private String digest(Object value) {
        try {
            byte[] bytes = json(value).getBytes(StandardCharsets.UTF_8);
            return "sha256:" + HexFormatHolder.encode(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) { throw new IllegalStateException(exception); }
    }

    private static final class HexFormatHolder {
        private static String encode(byte[] bytes) {
            StringBuilder result = new StringBuilder(bytes.length * 2);
            for (byte value : bytes) result.append(String.format("%02x", value));
            return result.toString();
        }
    }
}
