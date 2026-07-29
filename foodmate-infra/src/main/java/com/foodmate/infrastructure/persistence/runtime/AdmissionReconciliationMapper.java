package com.foodmate.infrastructure.persistence.runtime;

import com.foodmate.application.runtime.persistence.AdmissionReconciliationStore;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface AdmissionReconciliationMapper extends AdmissionReconciliationStore {
    @Select(
            "SELECT agent_run_id,run_id FROM runtime_dispatch_outbox WHERE status='queued' AND queued_at<=CURRENT_TIMESTAMP-(#{timeoutSeconds}*INTERVAL '1 second') ORDER BY queued_at LIMIT #{limit}")
    List<RunRef> findQueueExpired(
            @Param("timeoutSeconds") int timeoutSeconds, @Param("limit") int limit);

    @Select(
            "SELECT d.agent_run_id,CAST(d.agent_run_id AS VARCHAR) AS run_id FROM agent_run_dispatches d JOIN agent_runs r ON r.agent_run_id=d.agent_run_id WHERE d.dispatch_arbitration_state='active' AND d.deadline_at<=CURRENT_TIMESTAMP AND r.status NOT IN ('completed','failed','cancelled','superseded','waiting_user') ORDER BY d.deadline_at LIMIT #{limit}")
    List<RunRef> findExecutionExpired(int limit);

    @Update(
            "UPDATE agent_runs SET status='failed',error_code=#{code},result_json=CAST(#{resultJson} AS jsonb),updated_at=CURRENT_TIMESTAMP WHERE agent_run_id=#{agentRunId} AND status NOT IN ('completed','failed','cancelled','superseded')")
    int failRun(
            @Param("agentRunId") long agentRunId,
            @Param("code") String code,
            @Param("resultJson") String resultJson);

    @Update(
            "UPDATE agent_run_dispatches SET dispatch_arbitration_state='expired',status='expired',finished_at=COALESCE(finished_at,CURRENT_TIMESTAMP),updated_at=CURRENT_TIMESTAMP WHERE agent_run_id=#{agentRunId} AND dispatch_arbitration_state='active'")
    int expireDispatches(long agentRunId);

    @Update(
            "UPDATE runtime_dispatch_outbox SET status=CASE WHEN status='queued' THEN 'failed' ELSE 'expired' END,owner_token=NULL,lease_until=NULL,last_error=#{code},updated_at=CURRENT_TIMESTAMP WHERE agent_run_id=#{agentRunId} AND status IN ('queued','pending','leased')")
    int failOutboxes(@Param("agentRunId") long agentRunId, @Param("code") String code);

    @Select(
            "SELECT sse_last_stream_seq+1 FROM agent_runs WHERE agent_run_id=#{agentRunId} FOR UPDATE")
    Long nextSseSequence(long agentRunId);

    @Insert(
            "INSERT INTO agent_run_sse_outbox(agent_run_sse_outbox_id,agent_run_id,sse_event_id,stream_seq,source_event_key,event_type,payload_json) VALUES (#{eventId},#{agentRunId},#{sseEventId},#{sequence},#{sourceKey},'run.failed',CAST(#{payload} AS jsonb))")
    void insertFailedEvent(
            @Param("eventId") long eventId,
            @Param("agentRunId") long agentRunId,
            @Param("sseEventId") String sseEventId,
            @Param("sequence") long sequence,
            @Param("sourceKey") String sourceKey,
            @Param("payload") String payload);

    @Update(
            "UPDATE agent_runs SET sse_last_stream_seq=#{sequence} WHERE agent_run_id=#{agentRunId}")
    void updateSseSequence(@Param("agentRunId") long agentRunId, @Param("sequence") long sequence);

    @Update(
            "<script>UPDATE runtime_dispatch_outbox SET status='pending',queued_at=NULL,next_attempt_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE run_id IN <foreach collection='runIds' item='runId' open='(' separator=',' close=')'>#{runId}</foreach> AND status='queued'</script>")
    void promoteOutboxes(@Param("runIds") List<String> runIds);
}
