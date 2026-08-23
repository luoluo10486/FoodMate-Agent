package com.foodmate.infrastructure.persistence.runtime;

import com.foodmate.application.runtime.port.out.RuntimeRepository.EventHead;
import com.foodmate.application.runtime.port.out.RuntimeRepository.EventRow;
import com.foodmate.shared.runtime.RunEvent;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface RuntimeGatewayMapper {
    @Select("SELECT request_fingerprint FROM runtime_dispatches WHERE dispatch_id=#{id}")
    String dispatchFingerprint(String id);

    @Select("SELECT request_fingerprint FROM runtime_cancels WHERE cancel_id=#{id}")
    String cancelFingerprint(String id);

    @Insert(
            "INSERT INTO runtime_runs(run_id,status) VALUES (#{runId},#{status}) ON CONFLICT (run_id) DO NOTHING")
    void createRun(String runId, String status);

    @Insert(
            "INSERT INTO runtime_dispatches(dispatch_id,run_id,request_fingerprint) VALUES (#{id},#{runId},#{fingerprint})")
    void insertDispatch(String id, String runId, String fingerprint);

    @Insert(
            "INSERT INTO runtime_cancels(cancel_id,run_id,request_fingerprint) VALUES (#{id},#{runId},#{fingerprint})")
    void insertCancel(String id, String runId, String fingerprint);

    @Select("SELECT status FROM runtime_runs WHERE run_id=#{runId}")
    String status(String runId);

    @Update(
            "UPDATE runtime_runs SET status=#{status},updated_at=CURRENT_TIMESTAMP WHERE run_id=#{runId}")
    void updateStatus(String runId, String status);

    @Select(
            "SELECT event_fingerprint FROM runtime_event_inbox WHERE run_id=#{runId} AND event_id=#{eventId}")
    String eventFingerprint(String runId, String eventId);

    @Select(
            "SELECT event_seq AS seq,state FROM runtime_event_inbox WHERE run_id=#{runId} ORDER BY event_seq DESC LIMIT 1")
    EventHead latestEvent(String runId);

    @Insert(
            "INSERT INTO runtime_event_inbox(run_id,event_id,event_seq,event_fingerprint,state,payload_json,occurred_at) VALUES (#{event.runId},#{event.eventId},#{event.eventSeq},#{fingerprint},#{event.state},CAST(#{payload} AS jsonb),#{event.occurredAt})")
    void insertEvent(RunEvent event, String fingerprint, String payload);

    @Select(
            "SELECT event_id AS id,event_seq AS seq,state,payload_json::text AS payload,occurred_at AS occurredAt FROM runtime_event_inbox WHERE run_id=#{runId} ORDER BY event_seq")
    List<EventRow> events(String runId);

    @Select("SELECT EXISTS(SELECT 1 FROM runtime_runs WHERE run_id=#{runId})")
    boolean runExists(String runId);

    @Insert(
            "INSERT INTO agent_runs(agent_run_id,session_id,user_message_id,status,trace_id,created_by) VALUES (#{runId},#{sessionId},#{messageId},#{status},#{traceId},#{userId}) ON CONFLICT (agent_run_id) DO NOTHING")
    void registerAgentRun(
            long runId, long sessionId, long messageId, String status, String traceId, long userId);

    @Select("SELECT created_by FROM agent_runs WHERE agent_run_id=#{runId} AND is_deleted=FALSE")
    Long owner(long runId);

    @Update(
            "UPDATE agent_runs SET status=#{status},result_json=CASE WHEN #{status}='completed' THEN CAST(#{payload} AS jsonb) ELSE result_json END,error_code=CASE WHEN #{status} IN ('failed','cancelled') THEN #{error} ELSE error_code END,updated_at=CURRENT_TIMESTAMP WHERE agent_run_id=#{runId}")
    void updateAgentRun(long runId, String status, String payload, String error);

    @Select("SELECT status FROM agent_runs WHERE agent_run_id=#{runId}")
    String agentStatus(long runId);
}
