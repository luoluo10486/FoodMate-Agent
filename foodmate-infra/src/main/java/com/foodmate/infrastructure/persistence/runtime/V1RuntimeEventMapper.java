package com.foodmate.infrastructure.persistence.runtime;

import com.foodmate.application.runtime.port.out.RuntimeEventRepository.*;
import com.foodmate.shared.runtime.V1RunEvent;
import java.math.BigDecimal;
import java.util.List;
import org.apache.ibatis.annotations.*;

@Mapper
public interface V1RuntimeEventMapper {
    @Select(
            "SELECT EXISTS(SELECT 1 FROM agent_runs WHERE agent_run_id=#{runId} AND is_deleted=FALSE)")
    boolean runExists(long runId);

    @Select(
            "SELECT request_hash FROM runtime_event_inbox_v2 WHERE agent_run_id=#{runId} AND event_id=#{eventId}")
    String eventHash(long runId, String eventId);

    @Select(
            "SELECT agent_run_dispatch_id AS id,last_event_seq AS lastEventSeq,dispatch_arbitration_state AS state,attempt FROM agent_run_dispatches WHERE agent_run_id=#{runId} AND dispatch_id=#{dispatchId}")
    DispatchRow dispatch(long runId, String dispatchId);

    @Insert(
            "INSERT INTO runtime_event_inbox_v2(runtime_event_inbox_id,agent_run_id,dispatch_id,attempt,event_id,event_seq,event_type,occurred_at,payload_json,request_hash,processing_status,applied_at) VALUES (#{id},#{runId},#{event.dispatchId},#{event.attempt},#{event.eventId},#{event.eventSeq},#{event.eventType},#{event.occurredAt},CAST(#{payload} AS jsonb),#{event.requestHash},'applied',CURRENT_TIMESTAMP)")
    void insertEvent(long id, long runId, V1RunEvent event, String payload);

    @Update(
            "UPDATE agent_run_dispatches SET last_event_seq=#{seq},accepted_at=COALESCE(accepted_at,CURRENT_TIMESTAMP),status=CASE WHEN #{status} IN ('completed','failed','cancelled') THEN 'delivered' ELSE status END,finished_at=CASE WHEN #{status} IN ('completed','failed','cancelled') THEN CURRENT_TIMESTAMP ELSE finished_at END,updated_at=CURRENT_TIMESTAMP WHERE agent_run_id=#{runId} AND dispatch_id=#{dispatchId}")
    void updateDispatch(long runId, String dispatchId, long seq, String status);

    @Update(
            "UPDATE agent_runs SET status=#{status},result_json=CASE WHEN #{status} IN ('completed','validating') THEN CAST(#{result} AS jsonb) ELSE result_json END,error_code=CASE WHEN #{status}='failed' THEN 'RUNTIME_FAILED' ELSE error_code END,updated_at=CURRENT_TIMESTAMP WHERE agent_run_id=#{runId} AND status NOT IN ('completed','failed','cancelled','superseded')")
    void updateRun(long runId, String status, String result);

    @Update("UPDATE agent_runs SET updated_at=CURRENT_TIMESTAMP WHERE agent_run_id=#{runId}")
    void touchRun(long runId);

    @Update("UPDATE agent_runs SET result_type=#{type} WHERE agent_run_id=#{runId}")
    void setResultType(long runId, String type);

    @Update(
            "UPDATE agent_run_cancellations SET status='acknowledged',acknowledged_at=COALESCE(acknowledged_at,CURRENT_TIMESTAMP),updated_at=CURRENT_TIMESTAMP WHERE agent_run_id=#{runId} AND dispatch_id=#{dispatchId} AND status IN ('requested','dispatched')")
    void acknowledgeCancel(long runId, String dispatchId);

    @Update(
            "UPDATE agent_run_cancellations SET status='resolved',acknowledged_at=COALESCE(acknowledged_at,CURRENT_TIMESTAMP),resolved_at=COALESCE(resolved_at,CURRENT_TIMESTAMP),updated_at=CURRENT_TIMESTAMP WHERE agent_run_id=#{runId} AND dispatch_id=#{dispatchId} AND status IN ('requested','dispatched','acknowledged')")
    void resolveCancel(long runId, String dispatchId);

    @Select("SELECT sse_last_stream_seq+1 FROM agent_runs WHERE agent_run_id=#{runId} FOR UPDATE")
    long lockNextSseSequence(long runId);

    @Insert(
            "INSERT INTO agent_run_sse_outbox(agent_run_sse_outbox_id,agent_run_id,sse_event_id,stream_seq,source_event_key,event_type,payload_json) VALUES (#{id},#{runId},#{sseId},#{seq},#{source},#{type},CAST(#{payload} AS jsonb))")
    void insertSse(
            long id,
            long runId,
            String sseId,
            long seq,
            String source,
            String type,
            String payload);

    @Update("UPDATE agent_runs SET sse_last_stream_seq=#{seq} WHERE agent_run_id=#{runId}")
    void updateSseSequence(long runId, long seq);

    @Update(
            "UPDATE runtime_dispatch_outbox SET status='pending',queued_at=NULL,next_attempt_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE run_id=#{runId} AND status='queued'")
    void promoteOutbox(String runId);

    @Select(
            "SELECT event_id AS eventId,dispatch_id AS dispatchId,attempt,event_seq AS seq,event_type AS type,occurred_at AS occurredAt,payload_json::text AS payload,request_hash AS hash FROM runtime_event_inbox_v2 WHERE agent_run_id=#{runId} ORDER BY event_seq")
    List<EventRow> events(long runId);

    @Select(
            "SELECT stream_seq AS seq,sse_event_id AS id,event_type AS type,payload_json::text AS payload FROM agent_run_sse_outbox WHERE agent_run_id=#{runId} AND stream_seq>#{after} ORDER BY stream_seq")
    List<SseRow> sseEvents(long runId, long after);

    @Select(
            "SELECT stream_seq FROM agent_run_sse_outbox WHERE agent_run_id=#{runId} AND sse_event_id=#{cursor}")
    Long cursor(long runId, String cursor);

    @Select("SELECT status FROM agent_runs WHERE agent_run_id=#{runId} AND is_deleted=FALSE")
    String status(long runId);

    @Select(
            "SELECT EXISTS(SELECT 1 FROM agent_runs r JOIN sessions s ON s.session_id=r.session_id WHERE r.agent_run_id=#{runId} AND r.created_by=#{userId} AND r.is_deleted=FALSE AND s.user_id=#{userId} AND s.is_deleted=FALSE)")
    boolean owned(long runId, long userId);

    @Insert(
            "INSERT INTO runtime_event_rejections(rejection_id,agent_run_id,dispatch_id,attempt,event_id,event_seq,request_hash,reason,error_code,raw_envelope_json) VALUES (#{id},#{runId},#{event.dispatchId},#{event.attempt},#{event.eventId},#{event.eventSeq},#{event.requestHash},#{reason},#{code},CAST(#{envelope} AS jsonb))")
    void reject(
            String id, long runId, V1RunEvent event, String reason, String code, String envelope);

    @Insert(
            "INSERT INTO model_usage_logs(model_usage_log_id,request_id,trace_id,scene,provider_code,model_name,usage_json,latency_ms,cost_amount,status,created_by,updated_by) VALUES (#{id},#{event.requestId},#{event.traceId},#{scene},#{provider},#{model},CAST(#{usage} AS jsonb),#{latency},#{cost},#{status},NULL,NULL) ON CONFLICT (request_id) DO NOTHING")
    void insertUsage(
            long id,
            V1RunEvent event,
            String scene,
            String provider,
            String model,
            String usage,
            Integer latency,
            BigDecimal cost,
            String status);

    @Select(
            "SELECT EXISTS(SELECT 1 FROM messages WHERE agent_run_id=#{runId} AND role='assistant' AND is_deleted=FALSE)")
    boolean assistantExists(long runId);

    @Select(
            "SELECT session_id AS sessionId,created_by AS userId FROM agent_runs WHERE agent_run_id=#{runId} FOR UPDATE")
    RunOwner lockOwner(long runId);

    @Select(
            "SELECT COALESCE(MAX(sequence_no),0)+1 FROM messages WHERE session_id=#{sessionId} AND is_deleted=FALSE")
    int nextMessageSequence(long sessionId);

    @Insert(
            "INSERT INTO messages(message_id,session_id,agent_run_id,role,content,structured_payload,sequence_no,created_by) VALUES (#{id},#{owner.sessionId},#{runId},'assistant',#{text},CAST(#{payload} AS jsonb),#{sequence},#{owner.userId})")
    void insertAssistant(
            long id, long runId, RunOwner owner, String text, String payload, int sequence);

    @Update(
            "UPDATE sessions SET last_message_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE session_id=#{sessionId}")
    void touchSession(long sessionId);
}
