package com.foodmate.infrastructure.persistence.runtime;

import com.foodmate.application.runtime.port.out.ToolGatewayPort.Audit;
import com.foodmate.application.runtime.port.out.ToolGatewayPort.RunContext;
import com.foodmate.application.runtime.port.out.ToolGatewayPort.ToolCall;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

/** 动态 SQL 只能从通过 Java SQL Guard 的 sql_read Proposal 调用。 */
@Mapper
public interface ToolGatewayMapper {
    @Select(
            "SELECT EXISTS(SELECT 1 FROM agent_runs WHERE agent_run_id=#{runId} AND is_deleted=FALSE)")
    boolean runExists(long runId);

    @Select(
            "SELECT s.user_id AS userId,r.session_id AS sessionId,COALESCE((SELECT MIN(datasource_id) FROM data_sources WHERE status='active' AND readonly=TRUE AND is_deleted=FALSE),0) AS datasourceId FROM agent_runs r JOIN sessions s ON s.session_id=r.session_id AND s.is_deleted=FALSE WHERE r.agent_run_id=#{runId} AND r.is_deleted=FALSE")
    RunContext runContext(long runId);

    @Insert(
            "INSERT INTO sql_query_audits(sql_audit_id,agent_run_id,sql_text,status,row_count,reject_reason,latency_ms,trace_id,created_by,updated_by) VALUES (#{id},#{runId},#{statement},#{status},#{rows},#{reason},#{latencyMs},#{traceId},0,0)")
    void audit(Audit audit);

    @Insert(
            "INSERT INTO tool_calls(tool_call_id,agent_run_id,tool_name,tool_version,input_json,output_json,status,latency_ms,error_code,trace_id) VALUES (#{id},#{runId},#{toolName},#{toolVersion},CAST(#{inputJson} AS jsonb),CAST(#{outputJson} AS jsonb),#{status},#{latencyMs},#{errorCode},#{traceId})")
    void recordToolCall(ToolCall toolCall);
}
