package com.foodmate.infrastructure.persistence.runtime;

import com.foodmate.application.runtime.port.out.OutboxRepository.OutboxSnapshot;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface DispatchOutboxMapper {
    @Select(
            "SELECT outbox_id AS id, payload_json::text AS payload FROM runtime_dispatch_outbox WHERE status='pending' AND next_attempt_at<=CURRENT_TIMESTAMP ORDER BY created_at LIMIT #{limit}")
    List<OutboxSnapshot> findPending(int limit);

    @Update(
            "UPDATE runtime_dispatch_outbox SET status='leased',owner_token=#{owner},lease_until=CURRENT_TIMESTAMP+INTERVAL '30 seconds',send_attempts=send_attempts+1,updated_at=CURRENT_TIMESTAMP WHERE outbox_id=#{outboxId} AND status='pending'")
    int lease(@Param("outboxId") long outboxId, @Param("owner") String owner);

    @Update(
            "UPDATE runtime_dispatch_outbox SET status='published',owner_token=NULL,lease_until=NULL,transport='rocketmq',mq_topic=#{topic},mq_message_id=#{messageId},published_at=CURRENT_TIMESTAMP,delivered_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE outbox_id=#{outboxId} AND status='leased'")
    void markPublished(
            @Param("outboxId") long outboxId,
            @Param("topic") String topic,
            @Param("messageId") String messageId);

    @Update(
            "UPDATE runtime_dispatch_outbox SET status='delivered',owner_token=NULL,lease_until=NULL,transport='http',delivered_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE outbox_id=#{outboxId} AND status='leased'")
    void markDelivered(long outboxId);

    @Update(
            "UPDATE runtime_dispatch_outbox SET status=CASE WHEN deadline_at<=CURRENT_TIMESTAMP THEN 'expired' ELSE 'pending' END,owner_token=NULL,lease_until=NULL,next_attempt_at=CURRENT_TIMESTAMP+INTERVAL '2 seconds',last_error=#{error},updated_at=CURRENT_TIMESTAMP WHERE outbox_id=#{outboxId}")
    void markFailed(@Param("outboxId") long outboxId, @Param("error") String error);
}
