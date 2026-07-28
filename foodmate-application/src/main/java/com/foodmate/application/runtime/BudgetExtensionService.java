package com.foodmate.application.runtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.foodmate.shared.id.IdGenerator;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 处理用户确认后的预算追加，并在同一事务中生成新的 dispatch attempt。 */
@Service
public class BudgetExtensionService {
    private final JdbcTemplate jdbc;
    private final IdGenerator ids;
    private final int maxTokens;
    private final BigDecimal maxCost;
    private final int ttlSeconds;
    private final AgentAdmissionService admission;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()
            .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

    public BudgetExtensionService(ObjectProvider<JdbcTemplate> jdbcProvider,
                                   IdGenerator ids,
                                   AgentAdmissionService admission,
                                   @Value("${FOODMATE_AGENT_MAX_TOKEN_EXTENSION_PER_CONFIRMATION:30000}") int maxTokens,
                                  @Value("${FOODMATE_AGENT_MAX_COST_EXTENSION_CNY_PER_CONFIRMATION:1.00}") BigDecimal maxCost,
                                  @Value("${FOODMATE_AGENT_BUDGET_EXTENSION_TTL_SECONDS:900}") int ttlSeconds) {
        this.jdbc = jdbcProvider.getIfAvailable();
        this.ids = ids;
        this.admission = admission;
        this.maxTokens = maxTokens;
        this.maxCost = maxCost;
        this.ttlSeconds = ttlSeconds;
    }

    @Transactional
    public ExtensionResult confirm(long userId, long runId, int additionalTokens, BigDecimal additionalCost, String confirmationDigest) {
        if (jdbc == null) throw new com.foodmate.shared.runtime.RuntimeException("RUNTIME_UNAVAILABLE", "database is not configured");
        if (additionalTokens <= 0 || additionalTokens > maxTokens || additionalCost == null || additionalCost.signum() <= 0 || additionalCost.compareTo(maxCost) > 0) {
            throw new com.foodmate.shared.runtime.RuntimeException("BUDGET_EXTENSION_LIMIT_EXCEEDED", "budget extension exceeds configured limit");
        }
        if (confirmationDigest == null || confirmationDigest.isBlank() || confirmationDigest.length() > 71) {
            throw new com.foodmate.shared.runtime.RuntimeException("BUDGET_CONFIRMATION_INVALID", "confirmation digest is required");
        }
        var run = jdbc.query("SELECT r.status,r.result_type,r.session_id FROM agent_runs r JOIN sessions s ON s.session_id=r.session_id WHERE r.agent_run_id=? AND r.created_by=? AND s.user_id=? AND r.is_deleted=FALSE AND s.is_deleted=FALSE FOR UPDATE",
                (rs, row) -> new RunRow(rs.getString(1), rs.getString(2), rs.getLong(3)), runId, userId, userId);
        if (run.isEmpty() || !("completed".equals(run.getFirst().status()) && "safety_degraded".equals(run.getFirst().resultType()))) {
            throw new com.foodmate.shared.runtime.RuntimeException("RUNTIME_STATE_CONFLICT", "run is not waiting for budget confirmation");
        }
        var repeated = jdbc.query("SELECT additional_tokens,additional_cost_cny,extension_no FROM agent_run_budget_extensions WHERE agent_run_id=? AND confirmation_digest=? AND status='confirmed'",
                (rs, row) -> new ExistingExtension(rs.getInt(1), rs.getBigDecimal(2), rs.getInt(3)), runId, confirmationDigest);
        if (!repeated.isEmpty()) {
            ExistingExtension existing = repeated.getFirst();
            if (existing.tokens() != additionalTokens || existing.cost().compareTo(additionalCost) != 0) {
                throw new com.foodmate.shared.runtime.RuntimeException("BUDGET_CONFIRMATION_INVALID", "confirmation digest does not match the requested budget");
            }
            var currentDispatch = jdbc.query("SELECT dispatch_id,attempt,revision FROM agent_run_dispatches d JOIN agent_run_budget_snapshots b ON b.agent_run_id=d.agent_run_id WHERE d.agent_run_id=? ORDER BY d.attempt DESC LIMIT 1",
                    (rs, row) -> new ExtensionResult(Long.toString(runId), rs.getString(1), rs.getInt(2), rs.getInt(3), "queued"), runId);
            if (!currentDispatch.isEmpty()) return currentDispatch.getFirst();
            throw new com.foodmate.shared.runtime.RuntimeException("RUNTIME_STATE_CONFLICT", "confirmed budget dispatch is missing");
        }
        var snapshot = jdbc.query("SELECT revision,max_total_tokens,max_cost_cny,max_step_retries,max_replans,max_answer_rewrites,max_total_steps,max_model_calls,queue_timeout_seconds,execution_timeout_seconds,node_timeout_seconds,waiting_user_timeout_seconds,config_version FROM agent_run_budget_snapshots WHERE agent_run_id=? ORDER BY revision DESC LIMIT 1 FOR UPDATE",
                (rs, row) -> new Snapshot(rs.getInt(1), rs.getInt(2), rs.getBigDecimal(3), rs.getInt(4), rs.getInt(5), rs.getInt(6), rs.getInt(7), rs.getInt(8), rs.getInt(9), rs.getInt(10), rs.getInt(11), rs.getInt(12), rs.getString(13)), runId);
        if (snapshot.isEmpty()) throw new com.foodmate.shared.runtime.RuntimeException("RUNTIME_STATE_CONFLICT", "budget snapshot is missing");
        Snapshot current = snapshot.getFirst();
        int extensionNo = jdbc.queryForObject("SELECT COALESCE(MAX(extension_no),0)+1 FROM agent_run_budget_extensions WHERE agent_run_id=?", Integer.class, runId);
        long extensionId = ids.nextId();
        Instant expiresAt = Instant.now().plusSeconds(ttlSeconds);
        jdbc.update("INSERT INTO agent_run_budget_extensions(budget_extension_id,agent_run_id,extension_no,additional_tokens,additional_cost_cny,confirmation_digest,status,confirmed_at,expires_at) VALUES (?,?,?,?,?,?, 'confirmed',CURRENT_TIMESTAMP,?)",
                extensionId, runId, extensionNo, additionalTokens, additionalCost, confirmationDigest, java.sql.Timestamp.from(expiresAt));
        int revision = current.revision() + 1;
        jdbc.update("INSERT INTO agent_run_budget_snapshots(budget_snapshot_id,agent_run_id,revision,source,max_total_tokens,max_cost_cny,max_step_retries,max_replans,max_answer_rewrites,max_total_steps,max_model_calls,queue_timeout_seconds,execution_timeout_seconds,node_timeout_seconds,waiting_user_timeout_seconds,config_version,confirmation_digest) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                ids.nextId(), runId, revision, "extension", current.tokens() + additionalTokens, current.cost().add(additionalCost), current.stepRetries(), current.replans(), current.answerRewrites(), current.totalSteps(), current.modelCalls(), current.queueTimeout(), current.executionTimeout(), current.nodeTimeout(), current.waitingUserTimeout(), current.configVersion(), confirmationDigest);
        var previous = jdbc.query("SELECT d.agent_run_dispatch_id,d.attempt,d.active_epoch,o.payload_json FROM agent_run_dispatches d JOIN runtime_dispatch_outbox o ON o.agent_run_dispatch_id=d.agent_run_dispatch_id WHERE d.agent_run_id=? ORDER BY d.attempt DESC LIMIT 1 FOR UPDATE",
                (rs, row) -> new PreviousDispatch(rs.getLong(1), rs.getInt(2), rs.getLong(3), rs.getString(4)), runId);
        if (previous.isEmpty()) throw new com.foodmate.shared.runtime.RuntimeException("RUNTIME_STATE_CONFLICT", "active dispatch is missing");
        PreviousDispatch old = previous.getFirst();
        String dispatchId = "dsp_" + UUID.randomUUID().toString().replace("-", "");
        Instant deadline = Instant.now().plusSeconds(current.executionTimeout());
        String payload = nextPayload(old.payload(), dispatchId, old.attempt() + 1, deadline, revision, current, additionalTokens, additionalCost);
        String requestHash = digestWithoutRequestHash(payload);
        payload = replaceRequestHash(payload, requestHash);
        long dispatchRowId = ids.nextId();
        jdbc.update("UPDATE agent_run_dispatches SET dispatch_arbitration_state='expired',status='expired',updated_at=CURRENT_TIMESTAMP WHERE agent_run_dispatch_id=?", old.dispatchRowId());
        jdbc.update("UPDATE runtime_dispatch_outbox SET status='expired',updated_at=CURRENT_TIMESTAMP WHERE agent_run_dispatch_id=?", old.dispatchRowId());
        jdbc.update("INSERT INTO agent_run_dispatches(agent_run_dispatch_id,agent_run_id,dispatch_id,attempt,active_epoch,fencing_token,admission_epoch,deadline_at) VALUES (?,?,?,?,?,?,?,?)",
                dispatchRowId, runId, dispatchId, old.attempt() + 1, old.epoch() + 1, "fence_" + UUID.randomUUID().toString().replace("-", ""), 0, java.sql.Timestamp.from(deadline));
        jdbc.update("INSERT INTO runtime_dispatch_outbox(outbox_id,agent_run_dispatch_id,agent_run_id,dispatch_id,run_id,attempt,schema_version,deadline_at,fencing_epoch,payload_json,request_hash) VALUES (?,?,?,?,?,?,?,?,?,CAST(? AS jsonb),?)",
                ids.nextId(), dispatchRowId, runId, dispatchId, Long.toString(runId), old.attempt() + 1, "v1", java.sql.Timestamp.from(deadline), old.epoch() + 1, payload, requestHash);
        AgentAdmissionService.Admission admissionResult = admission.admit(Long.toString(runId), userId, run.getFirst().sessionId(), 20);
        if (admissionResult.state() == AgentAdmissionService.State.QUEUED) {
            jdbc.update("UPDATE runtime_dispatch_outbox SET status='queued',queued_at=CURRENT_TIMESTAMP,queue_priority=20,updated_at=CURRENT_TIMESTAMP WHERE agent_run_id=? AND dispatch_id=?", runId, dispatchId);
        }
        jdbc.update("UPDATE agent_runs SET status='queued',result_type=NULL,error_code=NULL,active_dispatch_id=?,updated_at=CURRENT_TIMESTAMP WHERE agent_run_id=?", dispatchRowId, runId);
        return new ExtensionResult(Long.toString(runId), dispatchId, old.attempt() + 1, revision, "queued");
    }

    private String nextPayload(String payload, String dispatchId, int attempt, Instant deadline, int revision, Snapshot current, int tokens, BigDecimal cost) {
        try {
            ObjectNode root = (ObjectNode) mapper.readTree(payload);
            root.put("dispatch_id", dispatchId);
            root.put("attempt", attempt);
            root.put("request_id", "req_" + UUID.randomUUID().toString().replace("-", ""));
            root.put("deadline_at", deadline.toString());
            ObjectNode options = (ObjectNode) root.with("runtime_options");
            ObjectNode budget = (ObjectNode) options.with("budget_snapshot");
            budget.put("max_total_tokens", current.tokens() + tokens);
            budget.put("max_cost_cny", current.cost().add(cost));
            budget.put("revision", revision);
            return mapper.writeValueAsString(root);
        } catch (JsonProcessingException | ClassCastException exception) {
            throw new com.foodmate.shared.runtime.RuntimeException("RUNTIME_CONTRACT_INVALID", "stored dispatch payload is invalid");
        }
    }

    private String replaceRequestHash(String payload, String hash) {
        try { ObjectNode root = (ObjectNode) mapper.readTree(payload); root.put("request_hash", hash); return mapper.writeValueAsString(root); }
        catch (JsonProcessingException exception) { throw new com.foodmate.shared.runtime.RuntimeException("RUNTIME_CONTRACT_INVALID", "dispatch payload is invalid"); }
    }

    private String digestWithoutRequestHash(String payload) {
        try {
            ObjectNode root = (ObjectNode) mapper.readTree(payload);
            root.remove("request_hash");
            byte[] bytes = mapper.writeValueAsBytes(root);
            return "sha256:" + hex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception exception) { throw new com.foodmate.shared.runtime.RuntimeException("RUNTIME_CONTRACT_INVALID", "cannot hash dispatch payload"); }
    }

    private static String hex(byte[] bytes) { StringBuilder value = new StringBuilder(); for (byte item : bytes) value.append(String.format("%02x", item)); return value.toString(); }

    private record RunRow(String status, String resultType, long sessionId) {}
    private record Snapshot(int revision, int tokens, BigDecimal cost, int stepRetries, int replans, int answerRewrites, int totalSteps, int modelCalls, int queueTimeout, int executionTimeout, int nodeTimeout, int waitingUserTimeout, String configVersion) {}
    private record PreviousDispatch(long dispatchRowId, int attempt, long epoch, String payload) {}
    private record ExistingExtension(int tokens, BigDecimal cost, int extensionNo) {}
    public record ExtensionResult(String runId, String dispatchId, int attempt, int budgetRevision, String status) {}
}
