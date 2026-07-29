package com.foodmate.infrastructure.persistence.runtime;

import com.foodmate.application.runtime.persistence.DlqStore;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface DlqMapper extends DlqStore {
    @Insert(
            """
            INSERT INTO runtime_message_dlq(dlq_id,consumer_group,source_topic,mq_message_id,message_key,
                run_id,dispatch_id,attempt,event_id,event_seq,request_hash,reconsume_times,error_code,last_error,raw_payload_json)
            VALUES (#{id},#{group},#{topic},#{messageId},#{messageKey},#{runId},#{dispatchId},#{attempt},#{eventId},#{eventSeq},#{requestHash},#{reconsumeTimes},#{errorCode},#{lastError},CAST(#{payload} AS jsonb))
            ON CONFLICT (consumer_group,mq_message_id) DO NOTHING
            """)
    void insert(DlqMessage message);

    @Select(
            "SELECT dlq_id AS id,run_id AS runId,event_id AS eventId FROM runtime_message_dlq WHERE reconciliation_state='pending' ORDER BY first_seen_at LIMIT #{limit}")
    List<DlqEntry> findPending(int limit);

    @Select(
            "SELECT COUNT(1) FROM runtime_event_inbox_v2 WHERE agent_run_id=#{runId} AND event_id=#{eventId}")
    long inboxCount(@Param("runId") long runId, @Param("eventId") String eventId);

    @Select("SELECT status FROM agent_runs WHERE agent_run_id=#{runId}")
    List<String> findRunStatuses(long runId);

    @Update(
            "UPDATE runtime_message_dlq SET reconciliation_state=#{state},reconciled_at=CURRENT_TIMESTAMP,reconciliation_note=#{note},updated_at=CURRENT_TIMESTAMP WHERE dlq_id=#{dlqId} AND reconciliation_state='pending'")
    int resolve(
            @Param("dlqId") long dlqId, @Param("state") String state, @Param("note") String note);
}
