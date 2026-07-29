package com.foodmate.infrastructure.persistence.runtime;

import com.foodmate.application.runtime.persistence.ProposalInboxStore;
import java.util.Map;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ProposalInboxMapper extends ProposalInboxStore {
    @Insert(
            "INSERT INTO runtime_tool_proposal_inbox(proposal_id,request_hash,payload_json,status) VALUES (#{proposalId},#{requestHash},CAST(#{payload} AS jsonb),'claimed') ON CONFLICT (proposal_id) DO NOTHING")
    int claim(
            @Param("proposalId") String proposalId,
            @Param("requestHash") String requestHash,
            @Param("payload") String payload);

    @Select(
            "SELECT request_hash,result_json::text AS result_json,status FROM runtime_tool_proposal_inbox WHERE proposal_id=#{proposalId}")
    Map<String, Object> find(String proposalId);

    @Update(
            "UPDATE runtime_tool_proposal_inbox SET status='completed',result_json=CAST(#{resultJson} AS jsonb),completed_at=CURRENT_TIMESTAMP WHERE proposal_id=#{proposalId}")
    int complete(@Param("proposalId") String proposalId, @Param("resultJson") String resultJson);
}
