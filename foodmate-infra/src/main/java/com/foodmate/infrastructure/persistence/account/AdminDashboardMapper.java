package com.foodmate.infrastructure.persistence.account;

import com.foodmate.application.account.port.out.AdminDashboardRepository.DeletedRow;
import com.foodmate.application.account.port.out.AdminDashboardRepository.KnowledgeRow;
import com.foodmate.application.account.port.out.AdminDashboardRepository.OperationAuditRow;
import com.foodmate.application.account.port.out.AdminDashboardRepository.Overview;
import com.foodmate.application.account.port.out.AdminDashboardRepository.RunRow;
import com.foodmate.application.account.port.out.AdminDashboardRepository.SqlAuditRow;
import com.foodmate.application.account.port.out.AdminDashboardRepository.ToolCallRow;
import com.foodmate.application.account.port.out.AdminDashboardRepository.ToolRow;
import com.foodmate.application.account.port.out.AdminDashboardRepository.UsageRow;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AdminDashboardMapper {
    @Select(
            "SELECT COUNT(*) FILTER (WHERE created_at>=CURRENT_DATE) AS runs_today,COALESCE(ROUND(100.0*COUNT(*) FILTER (WHERE status='failed')/NULLIF(COUNT(*),0),1),0) AS failure_rate,COUNT(*) AS total_runs FROM agent_runs WHERE is_deleted=FALSE")
    Overview overview();

    @Select("SELECT COUNT(*) FROM model_usage_logs WHERE is_deleted=FALSE")
    long modelUsageCount();

    @Select("SELECT COUNT(*) FROM knowledge_documents WHERE is_deleted=FALSE")
    long knowledgeCount();

    @Select(
            "SELECT r.agent_run_id,r.session_id,r.intent,r.status,r.trace_id,EXTRACT(EPOCH FROM (r.updated_at-r.created_at))*1000 AS duration_ms,u.username FROM agent_runs r JOIN sessions s ON s.session_id=r.session_id LEFT JOIN users u ON u.user_id=s.user_id WHERE r.is_deleted=FALSE ORDER BY r.created_at DESC LIMIT 100")
    List<RunRow> runs();

    @Select(
            "SELECT tool_call_id,agent_run_id,tool_name,status,latency_ms,trace_id FROM tool_calls WHERE is_deleted=FALSE ORDER BY created_at DESC LIMIT 100")
    List<ToolCallRow> toolCalls();

    @Select(
            "SELECT sql_audit_id,created_by AS actor,CONCAT('query_hash:',MD5(COALESCE(sql_text,original_question,''))) AS statement,status AS result,trace_id FROM sql_query_audits WHERE is_deleted=FALSE ORDER BY created_at DESC LIMIT 100")
    List<SqlAuditRow> sqlAudits();

    @Select(
            "SELECT name,COALESCE(current_version,'-') AS version,risk_level AS risk,status,availability_scope AS scope,category AS owner,COALESCE(updated_at::text,'-') AS last_called_at,revision FROM tool_registries WHERE is_deleted=FALSE ORDER BY name")
    List<ToolRow> tools();

    @Select(
            "SELECT provider_code AS provider,model_name AS model,scene,COALESCE((usage_json->>'total_tokens'),'0') AS tokens,cost_amount AS cost,latency_ms,status FROM model_usage_logs WHERE is_deleted=FALSE ORDER BY created_at DESC LIMIT 100")
    List<UsageRow> usage();

    @Select(
            "SELECT document_id,title,status,visibility,(SELECT COUNT(*) FROM knowledge_chunks c WHERE c.document_id=d.document_id AND c.is_deleted=FALSE) AS chunks,created_by AS owner,COALESCE(source_name,source_type,'-') AS source,CASE WHEN status='indexed' THEN '100%' WHEN status='parsed' THEN '70%' ELSE '0%' END AS index_progress,updated_at FROM knowledge_documents d WHERE is_deleted=FALSE ORDER BY updated_at DESC LIMIT 100")
    List<KnowledgeRow> knowledge();

    @Select(
            "SELECT 'user' AS resource_type,user_id AS resource_id,username AS owner,deleted_at,'账号注销' AS reason FROM users WHERE is_deleted=TRUE ORDER BY deleted_at DESC LIMIT 100")
    List<DeletedRow> deleted();

    @Select(
            "SELECT operator_id,action,target_type,target_id,result,request_id,trace_id,created_at FROM operation_audits WHERE is_deleted=FALSE ORDER BY created_at DESC LIMIT 100")
    List<OperationAuditRow> operationAudits();
}
