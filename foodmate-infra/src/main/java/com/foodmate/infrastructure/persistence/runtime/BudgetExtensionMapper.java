package com.foodmate.infrastructure.persistence.runtime;

import com.foodmate.application.runtime.persistence.BudgetExtensionStore;
import java.math.BigDecimal;
import java.time.Instant;
import org.apache.ibatis.annotations.*;

@Mapper
public interface BudgetExtensionMapper extends BudgetExtensionStore {
    @Select(
            "SELECT r.status,r.result_type AS resultType,r.session_id AS sessionId FROM agent_runs r JOIN sessions s ON s.session_id=r.session_id WHERE r.agent_run_id=#{runId} AND r.created_by=#{userId} AND s.user_id=#{userId} AND r.is_deleted=FALSE AND s.is_deleted=FALSE FOR UPDATE")
    RunRow lockRun(long runId, long userId);

    @Select(
            "SELECT additional_tokens AS tokens,additional_cost_cny AS cost,extension_no AS extensionNo FROM agent_run_budget_extensions WHERE agent_run_id=#{runId} AND confirmation_digest=#{digest} AND status='confirmed'")
    ExistingExtension findConfirmed(long runId, String digest);

    @Select(
            "SELECT d.dispatch_id AS dispatchId,d.attempt,b.revision FROM agent_run_dispatches d JOIN agent_run_budget_snapshots b ON b.agent_run_id=d.agent_run_id WHERE d.agent_run_id=#{runId} ORDER BY d.attempt DESC,b.revision DESC LIMIT 1")
    DispatchResult latestDispatchResult(long runId);

    @Select(
            "SELECT revision,max_total_tokens AS tokens,max_cost_cny AS cost,max_step_retries AS stepRetries,max_replans AS replans,max_answer_rewrites AS answerRewrites,max_total_steps AS totalSteps,max_model_calls AS modelCalls,queue_timeout_seconds AS queueTimeout,execution_timeout_seconds AS executionTimeout,node_timeout_seconds AS nodeTimeout,waiting_user_timeout_seconds AS waitingUserTimeout,config_version AS configVersion FROM agent_run_budget_snapshots WHERE agent_run_id=#{runId} ORDER BY revision DESC LIMIT 1 FOR UPDATE")
    Snapshot lockLatestSnapshot(long runId);

    @Select(
            "SELECT COALESCE(MAX(extension_no),0)+1 FROM agent_run_budget_extensions WHERE agent_run_id=#{runId}")
    int nextExtensionNo(long runId);

    @Insert(
            "INSERT INTO agent_run_budget_extensions(budget_extension_id,agent_run_id,extension_no,additional_tokens,additional_cost_cny,confirmation_digest,status,confirmed_at,expires_at) VALUES (#{id},#{runId},#{no},#{tokens},#{cost},#{digest},'confirmed',CURRENT_TIMESTAMP,#{expiresAt})")
    void insertExtension(
            long id,
            long runId,
            int no,
            int tokens,
            BigDecimal cost,
            String digest,
            Instant expiresAt);

    @Insert(
            "INSERT INTO agent_run_budget_snapshots(budget_snapshot_id,agent_run_id,revision,source,max_total_tokens,max_cost_cny,max_step_retries,max_replans,max_answer_rewrites,max_total_steps,max_model_calls,queue_timeout_seconds,execution_timeout_seconds,node_timeout_seconds,waiting_user_timeout_seconds,config_version,confirmation_digest) VALUES (#{id},#{runId},#{revision},'extension',#{tokens},#{cost},#{source.stepRetries},#{source.replans},#{source.answerRewrites},#{source.totalSteps},#{source.modelCalls},#{source.queueTimeout},#{source.executionTimeout},#{source.nodeTimeout},#{source.waitingUserTimeout},#{source.configVersion},#{digest})")
    void insertSnapshot(
            long id,
            long runId,
            int revision,
            int tokens,
            BigDecimal cost,
            Snapshot source,
            String digest);

    @Select(
            "SELECT d.agent_run_dispatch_id AS dispatchRowId,d.attempt,d.active_epoch AS epoch,o.payload_json::text AS payload FROM agent_run_dispatches d JOIN runtime_dispatch_outbox o ON o.agent_run_dispatch_id=d.agent_run_dispatch_id WHERE d.agent_run_id=#{runId} ORDER BY d.attempt DESC LIMIT 1 FOR UPDATE")
    PreviousDispatch lockPreviousDispatch(long runId);

    @Update(
            "UPDATE agent_run_dispatches SET dispatch_arbitration_state='expired',status='expired',updated_at=CURRENT_TIMESTAMP WHERE agent_run_dispatch_id=#{dispatchRowId}")
    void expireDispatch(long dispatchRowId);

    @Update(
            "UPDATE runtime_dispatch_outbox SET status='expired',updated_at=CURRENT_TIMESTAMP WHERE agent_run_dispatch_id=#{dispatchRowId}")
    void expireOutbox(long dispatchRowId);

    @Insert(
            "INSERT INTO agent_run_dispatches(agent_run_dispatch_id,agent_run_id,dispatch_id,attempt,active_epoch,fencing_token,admission_epoch,deadline_at) VALUES (#{rowId},#{runId},#{dispatchId},#{attempt},#{epoch},#{fence},0,#{deadline})")
    void insertDispatch(
            long rowId,
            long runId,
            String dispatchId,
            int attempt,
            long epoch,
            String fence,
            Instant deadline);

    @Insert(
            "INSERT INTO runtime_dispatch_outbox(outbox_id,agent_run_dispatch_id,agent_run_id,dispatch_id,run_id,attempt,schema_version,deadline_at,fencing_epoch,payload_json,request_hash) VALUES (#{outboxId},#{rowId},#{runId},#{dispatchId},#{runId}::text,#{attempt},'v1',#{deadline},#{epoch},CAST(#{payload} AS jsonb),#{hash})")
    void insertOutbox(
            long outboxId,
            long rowId,
            long runId,
            String dispatchId,
            int attempt,
            Instant deadline,
            long epoch,
            String payload,
            String hash);

    @Update(
            "UPDATE runtime_dispatch_outbox SET status='queued',queued_at=CURRENT_TIMESTAMP,queue_priority=#{priority},updated_at=CURRENT_TIMESTAMP WHERE agent_run_id=#{runId} AND dispatch_id=#{dispatchId}")
    void markOutboxQueued(long runId, String dispatchId, int priority);

    @Update(
            "UPDATE agent_runs SET status='queued',result_type=NULL,error_code=NULL,active_dispatch_id=#{dispatchRowId},updated_at=CURRENT_TIMESTAMP WHERE agent_run_id=#{runId}")
    void markRunQueued(long runId, long dispatchRowId);
}
