package com.foodmate.infrastructure.persistence.runtime;

import com.foodmate.application.runtime.port.out.InboxRepository.InboxRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ProposalInboxMapper {
    @Insert(
            // claimed 是带租约的处理中状态；超过 5 分钟仍未完成时允许重试者重新取得执行权，避免旧消息永久占满消费线程。
            "INSERT INTO runtime_tool_proposal_inbox(proposal_id,request_hash,payload_json,status) VALUES (#{proposalId},#{requestHash},CAST(#{payload} AS jsonb),'claimed') ON CONFLICT (proposal_id) DO UPDATE SET claimed_at=CURRENT_TIMESTAMP WHERE runtime_tool_proposal_inbox.status='claimed' AND runtime_tool_proposal_inbox.claimed_at < CURRENT_TIMESTAMP - INTERVAL '5 minutes'")
    int claim(
            @Param("proposalId") String proposalId,
            @Param("requestHash") String requestHash,
            @Param("payload") String payload);

    @Select(
            "SELECT request_hash,result_json::text AS result_json,status,"
                    + "payload_json->>'run_id' AS runId,payload_json->>'dispatch_id' AS dispatchId,"
                    + "COALESCE((payload_json->>'attempt')::int,0) AS attempt,"
                    + "payload_json->'payload'->>'invocation_id' AS invocationId,"
                    + "COALESCE(payload_json->>'tool_name',"
                    + "CASE WHEN payload_json->>'proposal_type'='sql_read' THEN 'database_query' END) AS toolName "
                    + "FROM runtime_tool_proposal_inbox WHERE proposal_id=#{proposalId}")
    InboxRecord find(String proposalId);

    @Update(
            "UPDATE runtime_tool_proposal_inbox SET status='executing',execution_started_at=CURRENT_TIMESTAMP "
                    + "WHERE proposal_id=#{proposalId} AND status='claimed'")
    int markExecuting(@Param("proposalId") String proposalId);

    @Update(
            "UPDATE runtime_tool_proposal_inbox SET status='skip_requested',skip_requested_at=CURRENT_TIMESTAMP "
                    + "WHERE proposal_id=#{proposalId} AND status='claimed'")
    int requestSkip(@Param("proposalId") String proposalId);

    @Update(
            "UPDATE runtime_tool_proposal_inbox SET status='completed',result_json=CAST(#{resultJson} AS jsonb),completed_at=CURRENT_TIMESTAMP WHERE proposal_id=#{proposalId} AND status IN ('claimed','executing','skip_requested')")
    int complete(@Param("proposalId") String proposalId, @Param("resultJson") String resultJson);
}
