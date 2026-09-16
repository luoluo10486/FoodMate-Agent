package com.foodmate.infrastructure.persistence.runtime;

import com.foodmate.application.runtime.port.out.ToolSkipRepository.NewSkip;
import com.foodmate.application.runtime.port.out.ToolSkipRepository.PendingSkip;
import com.foodmate.application.runtime.port.out.ToolSkipRepository.SkipRecord;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** PostgreSQL 单个工具步骤跳过请求映射。 */
@Mapper
public interface ToolSkipMapper {
    @Select(
            "SELECT agent_run_tool_skip_id AS rowId,skip_id AS skipId,agent_run_id::text AS runId,"
                    + "proposal_id AS proposalId,invocation_id AS invocationId,tool_name AS toolName,dispatch_id AS dispatchId,"
                    + "attempt,request_hash AS requestHash,proposal_request_hash AS proposalRequestHash,"
                    + "reason,status,transport,mq_message_id AS messageId "
                    + "FROM agent_run_tool_skips WHERE proposal_id=#{proposalId}")
    SkipRecord findByProposalId(@Param("proposalId") String proposalId);

    @Insert(
            "INSERT INTO agent_run_tool_skips(agent_run_tool_skip_id,skip_id,agent_run_id,proposal_id,"
                    + "invocation_id,tool_name,dispatch_id,attempt,request_hash,proposal_request_hash,reason,status,requested_at) "
                    + "VALUES (#{rowId},#{skipId},#{runId},#{proposalId},#{invocationId},#{toolName},#{dispatchId},#{attempt},"
                    + "#{requestHash},#{proposalRequestHash},#{reason},'requested',#{requestedAt})")
    void insertRequested(NewSkip skip);

    @Select(
            "SELECT agent_run_tool_skip_id AS rowId,skip_id AS skipId,agent_run_id::text AS runId,"
                    + "proposal_id AS proposalId,invocation_id AS invocationId,tool_name AS toolName,dispatch_id AS dispatchId,"
                    + "attempt,request_hash AS requestHash,proposal_request_hash AS proposalRequestHash,reason,requested_at AS requestedAt "
                    + "FROM agent_run_tool_skips WHERE status='requested' ORDER BY created_at LIMIT #{limit}")
    List<PendingSkip> findRequested(@Param("limit") int limit);

    @Update(
            "UPDATE agent_run_tool_skips SET status='dispatched',transport=#{transport},mq_message_id=#{messageId},"
                    + "published_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP "
                    + "WHERE agent_run_tool_skip_id=#{rowId} AND status='requested'")
    int markDispatched(
            @Param("rowId") long rowId,
            @Param("transport") String transport,
            @Param("messageId") String messageId);

    @Update(
            "UPDATE agent_run_tool_skips SET status='applied',applied_at=CURRENT_TIMESTAMP,updated_at=CURRENT_TIMESTAMP "
                    + "WHERE proposal_id=#{proposalId} AND status IN ('requested','dispatched')")
    int markApplied(@Param("proposalId") String proposalId);
}
