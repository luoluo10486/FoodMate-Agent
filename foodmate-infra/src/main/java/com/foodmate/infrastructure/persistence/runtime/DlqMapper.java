package com.foodmate.infrastructure.persistence.runtime;

import com.foodmate.application.runtime.port.out.DeadLetterRepository.DlqEntry;
import com.foodmate.application.runtime.port.out.DeadLetterRepository.DlqMessage;
import com.foodmate.application.runtime.port.out.DeadLetterRepository.ReplayCandidate;
import com.foodmate.application.runtime.port.out.DeadLetterRepository.ReplayOutbox;
import com.foodmate.application.runtime.port.out.DeadLetterRepository.ReplayRequest;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface DlqMapper {
    @Insert(
            """
            INSERT INTO runtime_message_dlq(dlq_id,consumer_group,source_topic,mq_message_id,message_key,
                run_id,dispatch_id,attempt,event_id,event_seq,request_hash,reconsume_times,error_code,last_error,raw_payload_json,raw_payload_text)
            VALUES (#{id},#{group},#{topic},#{messageId},#{messageKey},#{runId},#{dispatchId},#{attempt},#{eventId},#{eventSeq},#{requestHash},#{reconsumeTimes},#{errorCode},#{lastError},CAST(#{payload} AS jsonb),#{payloadText})
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
            "UPDATE runtime_message_dlq SET reconciliation_state=#{state},reconciled_at=CURRENT_TIMESTAMP,reconciliation_note=#{note},updated_at=CURRENT_TIMESTAMP WHERE dlq_id=#{dlqId} AND reconciliation_state IN ('pending','needs_attention')")
    int resolve(
            @Param("dlqId") long dlqId, @Param("state") String state, @Param("note") String note);

    @Select(
            "SELECT dlq_id AS dlqId,consumer_group AS consumerGroup,source_topic AS sourceTopic,"
                    + "mq_message_id AS originalMessageId,message_key AS messageKey,run_id AS runId,"
                    + "dispatch_id AS dispatchId,attempt,event_id AS eventId,event_seq AS eventSeq,"
                    + "request_hash AS requestHash,raw_payload_text AS payload FROM runtime_message_dlq "
                    + "WHERE dlq_id=#{dlqId} AND reconciliation_state='needs_attention'")
    ReplayCandidate findReplayCandidate(@Param("dlqId") long dlqId);

    @Select(
            "SELECT replay_id AS replayId,dlq_id AS dlqId,operator_id AS operatorId,consumer_group AS "
                    + "consumerGroup,source_topic AS sourceTopic,original_message_id AS originalMessageId,"
                    + "message_key AS messageKey,run_id AS runId,dispatch_id AS dispatchId,attempt,"
                    + "event_id AS eventId,event_seq AS eventSeq,request_hash AS requestHash,"
                    + "payload_json::text AS payload FROM runtime_dlq_replay_outbox WHERE dlq_id=#{dlqId} "
                    + "AND status IN ('pending','leased','published') ORDER BY replay_id DESC LIMIT 1")
    ReplayOutbox findActiveReplay(@Param("dlqId") long dlqId);

    @Insert(
            "INSERT INTO runtime_dlq_replay_outbox(replay_id,dlq_id,operator_id,idempotency_key,"
                    + "consumer_group,source_topic,original_message_id,message_key,run_id,dispatch_id,"
                    + "attempt,event_id,event_seq,request_hash,payload_json) VALUES(#{request.replayId},"
                    + "#{request.dlqId},#{request.operatorId},#{request.idempotencyKey},"
                    + "#{request.candidate.consumerGroup},#{request.candidate.sourceTopic},"
                    + "#{request.candidate.originalMessageId},#{request.candidate.messageKey},"
                    + "#{request.candidate.runId},#{request.candidate.dispatchId},#{request.candidate.attempt},"
                    + "#{request.candidate.eventId},#{request.candidate.eventSeq},#{request.candidate.requestHash},"
                    + "CAST(#{request.candidate.payload} AS jsonb)) ON CONFLICT DO NOTHING")
    int insertReplay(@Param("request") ReplayRequest request);

    @Select(
            "SELECT replay_id AS replayId,dlq_id AS dlqId,operator_id AS operatorId,consumer_group AS "
                    + "consumerGroup,source_topic AS sourceTopic,original_message_id AS originalMessageId,"
                    + "message_key AS messageKey,run_id AS runId,dispatch_id AS dispatchId,attempt,"
                    + "event_id AS eventId,event_seq AS eventSeq,request_hash AS requestHash,"
                    + "payload_json::text AS payload FROM runtime_dlq_replay_outbox WHERE "
                    + "(status='pending' AND next_attempt_at<=CURRENT_TIMESTAMP) OR "
                    + "(status='leased' AND lease_until<CURRENT_TIMESTAMP) ORDER BY created_at LIMIT #{limit}")
    List<ReplayOutbox> findPendingReplay(@Param("limit") int limit);

    @Update(
            "UPDATE runtime_dlq_replay_outbox SET status='leased',owner_token=#{owner},"
                    + "lease_until=CURRENT_TIMESTAMP+INTERVAL '30 seconds',send_attempts=send_attempts+1,"
                    + "updated_at=CURRENT_TIMESTAMP WHERE replay_id=#{replayId} AND "
                    + "((status='pending' AND next_attempt_at<=CURRENT_TIMESTAMP) OR "
                    + "(status='leased' AND lease_until<CURRENT_TIMESTAMP))")
    int leaseReplay(@Param("replayId") long replayId, @Param("owner") String owner);

    @Update(
            "WITH eligible AS (SELECT r.replay_id,r.dlq_id FROM runtime_dlq_replay_outbox r "
                    + "JOIN runtime_message_dlq d ON d.dlq_id=r.dlq_id WHERE r.replay_id=#{replayId} "
                    + "AND r.owner_token=#{owner} AND r.status='leased' AND d.reconciliation_state='needs_attention'),"
                    + "resolved AS (UPDATE runtime_message_dlq d SET reconciliation_state='resolved_replayed',"
                    + "reconciled_at=CURRENT_TIMESTAMP,reconciliation_note=#{messageId},updated_at=CURRENT_TIMESTAMP "
                    + "FROM eligible e WHERE d.dlq_id=e.dlq_id RETURNING d.dlq_id) UPDATE "
                    + "runtime_dlq_replay_outbox r SET status='published',owner_token=NULL,lease_until=NULL,"
                    + "broker_message_id=#{messageId},published_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP "
                    + "FROM resolved d WHERE r.replay_id=#{replayId} AND r.dlq_id=d.dlq_id")
    int markReplayPublished(
            @Param("replayId") long replayId,
            @Param("owner") String owner,
            @Param("messageId") String messageId);

    @Update(
            "UPDATE runtime_dlq_replay_outbox SET status=CASE WHEN send_attempts>=3 THEN 'failed' ELSE 'pending' END,"
                    + "owner_token=NULL,lease_until=NULL,last_error=#{error},next_attempt_at="
                    + "CURRENT_TIMESTAMP+(LEAST(60,POWER(2,GREATEST(send_attempts-1,0)))*INTERVAL '1 second'),"
                    + "updated_at=CURRENT_TIMESTAMP WHERE replay_id=#{replayId} AND owner_token=#{owner} "
                    + "AND status='leased'")
    void retryReplay(
            @Param("replayId") long replayId,
            @Param("owner") String owner,
            @Param("error") String error);
}
