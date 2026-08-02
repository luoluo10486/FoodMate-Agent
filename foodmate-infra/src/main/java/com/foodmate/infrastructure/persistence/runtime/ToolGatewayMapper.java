package com.foodmate.infrastructure.persistence.runtime;

import com.foodmate.application.runtime.port.out.ToolGatewayPort.*;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 动态 SQL 只能从通过 Java SQL Guard 的 sql_read Proposal 调用。 */
@Mapper
public interface ToolGatewayMapper {
    @Select(
            "SELECT EXISTS(SELECT 1 FROM agent_runs WHERE agent_run_id=#{runId} AND is_deleted=FALSE)")
    boolean runExists(long runId);

    @Select("${statement}")
    List<Map<String, Object>> executeRead(@Param("statement") String statement);

    @Insert(
            "INSERT INTO sql_query_audits(sql_audit_id,agent_run_id,sql_text,status,row_count,reject_reason,latency_ms,trace_id,created_by,updated_by) VALUES (#{id},#{runId},#{statement},#{status},#{rows},#{reason},#{latencyMs},#{traceId},0,0)")
    void audit(Audit audit);
}
