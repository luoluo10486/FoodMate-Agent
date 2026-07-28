package com.foodmate.application.runtime;

import com.foodmate.shared.id.IdGenerator;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 收敛排队和执行超时。
 *
 * <p>Redis lease 不是最终事实，因此超时扫描必须以 PostgreSQL 的 deadline 和
 * Outbox 状态为准；扫描完成后再释放 Redis lease，防止 Java 进程重启后 Run 永久占位。
 */
@Component
public class RuntimeAdmissionReconciler {
    private final JdbcTemplate jdbc;
    private final AgentAdmissionService admission;
    private final IdGenerator ids;
    private final int queueTimeoutSeconds;

    public RuntimeAdmissionReconciler(ObjectProvider<JdbcTemplate> jdbcProvider,
                                      AgentAdmissionService admission,
                                      IdGenerator ids,
                                      @Value("${FOODMATE_AGENT_QUEUE_TIMEOUT_SECONDS:30}") int queueTimeoutSeconds) {
        this.jdbc = jdbcProvider.getIfAvailable();
        this.admission = admission;
        this.ids = ids;
        this.queueTimeoutSeconds = Math.max(1, queueTimeoutSeconds);
    }

    @Scheduled(fixedDelayString = "${foodmate.runtime.admission.reconcile-ms:1000}")
    @Transactional
    public void reconcile() {
        if (jdbc == null) return;
        try { admission.renewActiveLeases(); }
        catch (com.foodmate.shared.runtime.RuntimeException failure) {
            if (!"RUNTIME_COORDINATION_UNAVAILABLE".equals(failure.code())) throw failure;
        }
        List<RunRef> queued = jdbc.query("SELECT agent_run_id,run_id FROM runtime_dispatch_outbox WHERE status='queued' AND queued_at <= CURRENT_TIMESTAMP - (? * INTERVAL '1 second') ORDER BY queued_at LIMIT 20",
                (rs, row) -> new RunRef(rs.getLong(1), rs.getString(2)), queueTimeoutSeconds);
        queued.forEach(run -> fail(run, "RUNTIME_QUEUE_TIMEOUT", "Agent 排队超时"));

        List<RunRef> expired = jdbc.query("SELECT d.agent_run_id,CAST(d.agent_run_id AS VARCHAR) FROM agent_run_dispatches d JOIN agent_runs r ON r.agent_run_id=d.agent_run_id WHERE d.dispatch_arbitration_state='active' AND d.deadline_at <= CURRENT_TIMESTAMP AND r.status NOT IN ('completed','failed','cancelled','superseded','waiting_user') ORDER BY d.deadline_at LIMIT 20",
                (rs, row) -> new RunRef(rs.getLong(1), rs.getString(2)));
        expired.forEach(run -> fail(run, "RUNTIME_DEADLINE_EXCEEDED", "Agent 执行超时"));
    }

    protected void fail(RunRef run, String code, String message) {
        int updated = jdbc.update("UPDATE agent_runs SET status='failed',error_code=?,result_json=CAST(? AS jsonb),updated_at=CURRENT_TIMESTAMP WHERE agent_run_id=? AND status NOT IN ('completed','failed','cancelled','superseded')",
                code, "{\"error_code\":\"" + code + "\",\"message\":\"" + message + "\"}", run.agentRunId());
        if (updated == 0) {
            releaseBestEffort(run.runId());
            return;
        }
        jdbc.update("UPDATE agent_run_dispatches SET dispatch_arbitration_state='expired',status='expired',finished_at=COALESCE(finished_at,CURRENT_TIMESTAMP),updated_at=CURRENT_TIMESTAMP WHERE agent_run_id=? AND dispatch_arbitration_state='active'",
                run.agentRunId());
        jdbc.update("UPDATE runtime_dispatch_outbox SET status=CASE WHEN status='queued' THEN 'failed' ELSE 'expired' END,owner_token=NULL,lease_until=NULL,last_error=?,updated_at=CURRENT_TIMESTAMP WHERE agent_run_id=? AND status IN ('queued','pending','leased')",
                code, run.agentRunId());
        // 直接由 Java 超时裁决时也必须产生可恢复的 SSE 终态事件。
        Long next = jdbc.queryForObject("SELECT sse_last_stream_seq + 1 FROM agent_runs WHERE agent_run_id=? FOR UPDATE", Long.class, run.agentRunId());
        jdbc.update("INSERT INTO agent_run_sse_outbox(agent_run_sse_outbox_id,agent_run_id,sse_event_id,stream_seq,source_event_key,event_type,payload_json) VALUES (?,?,?,?,?,?,CAST(? AS jsonb))",
                ids.nextId(), run.agentRunId(), "sse_" + ids.nextId(), next, run.agentRunId() + ":timeout:" + code, "run.failed",
                "{\"error_code\":\"" + code + "\",\"message\":\"" + message + "\"}");
        jdbc.update("UPDATE agent_runs SET sse_last_stream_seq=? WHERE agent_run_id=?", next, run.agentRunId());
        for (String promotedRunId : releaseBestEffort(run.runId())) {
            jdbc.update("UPDATE runtime_dispatch_outbox SET status='pending',queued_at=NULL,next_attempt_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE run_id=? AND status='queued'", promotedRunId);
        }
    }

    private List<String> releaseBestEffort(String runId) {
        try { return admission.releaseAndPromote(runId); }
        catch (com.foodmate.shared.runtime.RuntimeException failure) {
            if ("RUNTIME_COORDINATION_UNAVAILABLE".equals(failure.code())) return List.of();
            throw failure;
        }
    }

    private record RunRef(long agentRunId, String runId) {}
}
