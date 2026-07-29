package com.foodmate.infrastructure.persistence.runtime;

import com.foodmate.application.runtime.persistence.CancellationStore;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CancellationMapper extends CancellationStore {
    @Select(
            "SELECT d.dispatch_id AS dispatchId,d.attempt,r.status AS runStatus FROM agent_runs r JOIN agent_run_dispatches d ON d.agent_run_dispatch_id=r.active_dispatch_id WHERE r.agent_run_id=#{runId} AND r.is_deleted=FALSE")
    ActiveDispatch findActiveDispatch(long runId);

    @Insert(
            "INSERT INTO agent_run_cancellations(cancellation_id,agent_run_id,cancel_id,dispatch_id,request_hash,reason,status,requested_at) VALUES (#{id},#{runId},#{cancelId},#{dispatchId},#{requestHash},#{reason},'requested',#{requestedAt})")
    void insertRequested(NewCancellation cancellation);

    @Update(
            "UPDATE agent_runs SET cancellation_epoch=cancellation_epoch+1,updated_at=CURRENT_TIMESTAMP WHERE agent_run_id=#{runId}")
    int incrementCancellationEpoch(long runId);

    @Select(
            "SELECT c.cancellation_id AS id,c.agent_run_id AS runId,c.cancel_id AS cancelId,c.dispatch_id AS dispatchId,d.attempt,c.request_hash AS requestHash,c.reason,c.requested_at AS requestedAt FROM agent_run_cancellations c JOIN agent_runs r ON r.agent_run_id=c.agent_run_id JOIN agent_run_dispatches d ON d.dispatch_id=c.dispatch_id WHERE c.status='requested' ORDER BY c.created_at LIMIT #{limit}")
    List<PendingCancellation> findRequested(int limit);

    @Update(
            "UPDATE agent_run_cancellations SET status='dispatched',transport=#{transport},mq_message_id=#{messageId},published_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP WHERE cancellation_id=#{cancellationId} AND status='requested'")
    int markDispatched(
            @Param("cancellationId") long cancellationId,
            @Param("transport") String transport,
            @Param("messageId") String messageId);
}
