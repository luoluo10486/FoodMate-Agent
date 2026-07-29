package com.foodmate.infrastructure.persistence.runtime;

import com.foodmate.application.runtime.persistence.RuntimeRecoveryStore;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** PostgreSQL authority used to reconcile a checkpoint before creating a new attempt. */
@Mapper
public interface RuntimeRecoveryMapper extends RuntimeRecoveryStore {
    @Select(
            """
            SELECT r.status,
                   r.session_id AS sessionId,
                   d.agent_run_dispatch_id AS dispatchRowId,
                   d.dispatch_id AS previousDispatchId,
                   d.attempt AS previousAttempt,
                   d.active_epoch AS previousEpoch,
                   d.deadline_at AS deadline,
                   COALESCE((SELECT MAX(revision) FROM agent_run_budget_snapshots b WHERE b.agent_run_id=r.agent_run_id), 0) AS budgetRevision,
                   o.payload_json::text AS payload
            FROM agent_runs r
            JOIN sessions s ON s.session_id=r.session_id AND s.user_id=#{userId} AND s.is_deleted=FALSE
            JOIN agent_run_dispatches d ON d.agent_run_dispatch_id=r.active_dispatch_id
            JOIN runtime_dispatch_outbox o ON o.agent_run_dispatch_id=d.agent_run_dispatch_id
            WHERE r.agent_run_id=#{runId} AND r.created_by=#{userId} AND r.is_deleted=FALSE
            FOR UPDATE
            """)
    RecoveryRun lockRun(long runId, long userId);

    @Select(
            "SELECT tool_call_id::text FROM tool_calls WHERE agent_run_id=#{runId} AND status='success' AND is_deleted=FALSE ORDER BY created_at, tool_call_id")
    List<String> completedInvocationIds(long runId);

    @Update(
            "UPDATE agent_run_dispatches SET dispatch_arbitration_state='expired', status='expired', updated_at=CURRENT_TIMESTAMP WHERE agent_run_dispatch_id=#{dispatchRowId} AND dispatch_arbitration_state='active'")
    void expireDispatch(long dispatchRowId);

    @Update(
            "UPDATE runtime_dispatch_outbox SET status='expired', updated_at=CURRENT_TIMESTAMP WHERE agent_run_dispatch_id=#{dispatchRowId} AND status IN ('pending','queued','leased')")
    void expireOutbox(long dispatchRowId);

    @Insert(
            "INSERT INTO agent_run_dispatches(agent_run_dispatch_id,agent_run_id,dispatch_id,attempt,active_epoch,fencing_token,admission_epoch,deadline_at) VALUES (#{rowId},#{runId},#{dispatchId},#{attempt},#{epoch},#{fencingToken},0,#{deadline})")
    void insertDispatch(
            long rowId,
            long runId,
            String dispatchId,
            int attempt,
            long epoch,
            String fencingToken,
            Instant deadline);

    @Insert(
            "INSERT INTO runtime_dispatch_outbox(outbox_id,agent_run_dispatch_id,agent_run_id,dispatch_id,run_id,attempt,schema_version,deadline_at,fencing_epoch,payload_json,request_hash) VALUES (#{outboxId},#{dispatchRowId},#{runId},#{dispatchId},#{runId}::text,#{attempt},'v1',#{deadline},#{epoch},CAST(#{payload} AS jsonb),#{requestHash})")
    void insertOutbox(
            long outboxId,
            long dispatchRowId,
            long runId,
            String dispatchId,
            int attempt,
            Instant deadline,
            long epoch,
            String payload,
            String requestHash);

    @Update(
            "UPDATE runtime_dispatch_outbox SET status='queued', queued_at=CURRENT_TIMESTAMP, queue_priority=#{priority}, updated_at=CURRENT_TIMESTAMP WHERE agent_run_id=#{runId} AND dispatch_id=#{dispatchId}")
    void markOutboxQueued(long runId, String dispatchId, int priority);

    @Update(
            "UPDATE agent_runs SET status='queued', result_type=NULL, error_code=NULL, active_dispatch_id=#{dispatchRowId}, updated_at=CURRENT_TIMESTAMP WHERE agent_run_id=#{runId} AND status NOT IN ('completed','failed','cancelled','superseded')")
    void markRunQueued(long runId, long dispatchRowId);
}
