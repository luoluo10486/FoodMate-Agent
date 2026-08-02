package com.foodmate.infrastructure.persistence.runtime;

import com.foodmate.application.runtime.port.out.RuntimeRecoveryRepository.*;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** PostgreSQL authority used to reconcile a checkpoint before creating a new attempt. */
@Mapper
public interface RuntimeRecoveryMapper {
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
            """
            SELECT (payload_json->>'checkpoint_version')::int AS version,
                   payload_json->>'checkpoint_digest' AS digest,
                   (payload_json->>'budget_revision')::int AS budgetRevision,
                   payload_json->>'current_node' AS currentNode,
                   COALESCE(payload_json->'completed_invocation_ids','[]'::jsonb)::text AS completedInvocationIdsJson
            FROM runtime_event_inbox_v2
            WHERE agent_run_id=#{runId}
              AND dispatch_id=#{dispatchId}
              AND event_type='run.checkpoint_saved'
            ORDER BY event_seq DESC
            LIMIT 1
            """)
    CheckpointFact latestCheckpoint(long runId, String dispatchId);

    @Select(
            "SELECT tool_call_id::text FROM tool_calls WHERE agent_run_id=#{runId} AND status='success' AND is_deleted=FALSE ORDER BY created_at, tool_call_id")
    List<String> completedInvocationIds(long runId);

    @Select(
            """
            SELECT jsonb_build_object(
                       'proposal_id', p.proposal_id,
                       'invocation_id', p.payload_json #>> '{payload,invocation_id}',
                       'request_hash', p.request_hash,
                       'status', COALESCE(p.result_json->>'status', 'failed'),
                       'error_code', COALESCE(p.result_json->>'errorCode', ''),
                       'rows', COALESCE(p.result_json->'rows', '[]'::jsonb)
                   )::text
            FROM runtime_tool_proposal_inbox p
            WHERE p.payload_json->>'run_id'=CAST(#{runId} AS text)
              AND p.status='completed'
              AND p.result_json IS NOT NULL
            ORDER BY p.completed_at, p.proposal_id
            """)
    List<String> completedToolResults(long runId);

    @Select(
            """
            WITH latest_checkpoint AS (
                SELECT DISTINCT ON (agent_run_id, dispatch_id)
                       agent_run_id, dispatch_id, attempt, event_seq, occurred_at
                FROM runtime_event_inbox_v2
                WHERE event_type='run.checkpoint_saved'
                  AND payload_json->>'current_node'='tool_wait'
                ORDER BY agent_run_id, dispatch_id, event_seq DESC
            )
            SELECT r.agent_run_id AS runId, r.created_by AS userId
            FROM latest_checkpoint c
            JOIN agent_runs r ON r.agent_run_id=c.agent_run_id AND r.is_deleted=FALSE
            JOIN agent_run_dispatches d ON d.agent_run_id=c.agent_run_id
                                        AND d.dispatch_id=c.dispatch_id
                                        AND d.attempt=c.attempt
                                        AND d.dispatch_arbitration_state='active'
            WHERE r.status NOT IN ('completed','failed','cancelled','superseded')
              AND c.occurred_at < CURRENT_TIMESTAMP - (#{staleSeconds} * INTERVAL '1 second')
              AND NOT EXISTS (
                  SELECT 1 FROM runtime_event_inbox_v2 newer
                  WHERE newer.agent_run_id=c.agent_run_id
                    AND newer.dispatch_id=c.dispatch_id
                    AND newer.attempt=c.attempt
                    AND newer.event_seq > c.event_seq
              )
            ORDER BY c.occurred_at
            LIMIT #{limit}
            """)
    List<RecoveryCandidate> findStaleToolWaitRuns(int staleSeconds, int limit);

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
