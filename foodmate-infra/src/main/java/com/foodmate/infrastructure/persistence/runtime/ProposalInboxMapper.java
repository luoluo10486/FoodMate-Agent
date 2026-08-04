package com.foodmate.infrastructure.persistence.runtime;

import com.foodmate.application.runtime.port.out.InboxRepository.*;
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
            "SELECT request_hash,result_json::text AS result_json,status FROM runtime_tool_proposal_inbox WHERE proposal_id=#{proposalId}")
    InboxRecord find(String proposalId);

    @Update(
            "UPDATE runtime_tool_proposal_inbox SET status='completed',result_json=CAST(#{resultJson} AS jsonb),completed_at=CURRENT_TIMESTAMP WHERE proposal_id=#{proposalId}")
    int complete(@Param("proposalId") String proposalId, @Param("resultJson") String resultJson);
}
