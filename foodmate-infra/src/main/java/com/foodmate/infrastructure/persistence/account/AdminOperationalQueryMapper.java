package com.foodmate.infrastructure.persistence.account;

import com.foodmate.application.account.port.out.AdminOperationalQueryRepository.DeletedRow;
import com.foodmate.application.account.port.out.AdminOperationalQueryRepository.DlqRow;
import com.foodmate.application.account.port.out.AdminOperationalQueryRepository.KnowledgeRow;
import com.foodmate.application.account.port.out.AdminOperationalQueryRepository.OperationAuditRow;
import com.foodmate.application.account.port.out.AdminOperationalQueryRepository.Query;
import com.foodmate.application.account.port.out.AdminOperationalQueryRepository.RunRow;
import com.foodmate.application.account.port.out.AdminOperationalQueryRepository.SqlAuditRow;
import com.foodmate.application.account.port.out.AdminOperationalQueryRepository.ToolCallRow;
import com.foodmate.application.account.port.out.AdminOperationalQueryRepository.ToolRow;
import com.foodmate.application.account.port.out.AdminOperationalQueryRepository.UsageRow;
import com.foodmate.application.account.port.out.AdminOperationalQueryRepository.UserRow;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** PostgreSQL 分页查询；每条 SQL 明确裁剪字段，禁止返回原文和存储对象键。 */
@Mapper
public interface AdminOperationalQueryMapper {
    @Select(
            "<script>SELECT user_id,username,role,status,CASE WHEN email IS NULL THEN NULL ELSE"
                    + " CONCAT('email-',MD5(email)) END AS email_ref FROM users WHERE"
                    + " is_deleted=FALSE<if test='q.text != null and q.text != &quot;&quot;'> AND"
                    + " username ILIKE CONCAT('%',#{q.text},'%')</if><if test='q.status != null and"
                    + " q.status != &quot;&quot;'> AND status=#{q.status}</if> ORDER BY <choose><when"
                    + " test=\"q.sort == 'username'\">username</when><when test=\"q.sort =="
                    + " 'status'\">status</when><otherwise>created_at</otherwise></choose>"
                    + " <choose><when test=\"q.direction =="
                    + " 'asc'\">ASC</when><otherwise>DESC</otherwise></choose>,user_id DESC LIMIT"
                    + " #{q.limit} OFFSET #{q.offset}</script>")
    List<UserRow> users(@Param("q") Query query);

    @Select(
            "<script>SELECT COUNT(*) FROM users WHERE is_deleted=FALSE<if test='q.text != null and"
                    + " q.text != &quot;&quot;'> AND username ILIKE CONCAT('%',#{q.text},'%')</if><if"
                    + " test='q.status != null and q.status != &quot;&quot;'> AND"
                    + " status=#{q.status}</if></script>")
    long countUsers(@Param("q") Query query);

    @Select(
            "<script>SELECT r.agent_run_id,r.session_id,r.intent,r.status,r.trace_id,EXTRACT(EPOCH"
                    + " FROM (r.updated_at-r.created_at))*1000 AS duration_ms,CASE WHEN s.user_id IS"
                    + " NULL THEN NULL ELSE CONCAT('user-',MD5(CAST(s.user_id AS TEXT))) END AS"
                    + " actor_ref FROM agent_runs r JOIN sessions s ON s.session_id=r.session_id LEFT"
                    + " JOIN users u ON u.user_id=s.user_id WHERE r.is_deleted=FALSE<if test='q.text !="
                    + " null and q.text != &quot;&quot;'> AND (r.intent ILIKE CONCAT('%',#{q.text},'%')"
                    + " OR r.trace_id ILIKE CONCAT('%',#{q.text},'%'))</if><if test='q.status != null"
                    + " and q.status != &quot;&quot;'> AND r.status=#{q.status}</if> ORDER BY"
                    + " <choose><when test=\"q.sort == 'duration_ms'\">duration_ms</when><when"
                    + " test=\"q.sort =="
                    + " 'status'\">r.status</when><otherwise>r.created_at</otherwise></choose>"
                    + " <choose><when test=\"q.direction =="
                    + " 'asc'\">ASC</when><otherwise>DESC</otherwise></choose>,r.agent_run_id DESC"
                    + " LIMIT #{q.limit} OFFSET #{q.offset}</script>")
    List<RunRow> runs(@Param("q") Query query);

    @Select(
            "<script>SELECT COUNT(*) FROM agent_runs r JOIN sessions s ON s.session_id=r.session_id"
                    + " WHERE r.is_deleted=FALSE<if test='q.text != null and q.text != &quot;&quot;'>"
                    + " AND (r.intent ILIKE CONCAT('%',#{q.text},'%') OR r.trace_id ILIKE"
                    + " CONCAT('%',#{q.text},'%'))</if><if test='q.status != null and q.status !="
                    + " &quot;&quot;'> AND r.status=#{q.status}</if></script>")
    long countRuns(@Param("q") Query query);

    @Select(
            "<script>SELECT tool_call_id,agent_run_id,tool_name,status,latency_ms,trace_id FROM"
                    + " tool_calls WHERE is_deleted=FALSE<if test='q.text != null and q.text !="
                    + " &quot;&quot;'> AND (tool_name ILIKE CONCAT('%',#{q.text},'%') OR trace_id ILIKE"
                    + " CONCAT('%',#{q.text},'%'))</if><if test='q.status != null and q.status !="
                    + " &quot;&quot;'> AND status=#{q.status}</if> ORDER BY <choose><when test=\"q.sort"
                    + " == 'latency_ms'\">latency_ms</when><when test=\"q.sort =="
                    + " 'status'\">status</when><otherwise>created_at</otherwise></choose>"
                    + " <choose><when test=\"q.direction =="
                    + " 'asc'\">ASC</when><otherwise>DESC</otherwise></choose>,tool_call_id DESC LIMIT"
                    + " #{q.limit} OFFSET #{q.offset}</script>")
    List<ToolCallRow> toolCalls(@Param("q") Query query);

    @Select(
            "<script>SELECT COUNT(*) FROM tool_calls WHERE is_deleted=FALSE<if test='q.text != null"
                    + " and q.text != &quot;&quot;'> AND (tool_name ILIKE CONCAT('%',#{q.text},'%') OR"
                    + " trace_id ILIKE CONCAT('%',#{q.text},'%'))</if><if test='q.status != null and"
                    + " q.status != &quot;&quot;'> AND status=#{q.status}</if></script>")
    long countToolCalls(@Param("q") Query query);

    @Select(
            "<script>SELECT sql_audit_id,created_by AS actor,MD5(COALESCE(sql_text,'')) AS"
                    + " query_hash,status AS result,trace_id,latency_ms,row_count,reject_reason AS"
                    + " error_code,created_at FROM sql_query_audits WHERE is_deleted=FALSE<if"
                    + " test='q.text != null and q.text != &quot;&quot;'> AND (trace_id ILIKE"
                    + " CONCAT('%',#{q.text},'%') OR status ILIKE CONCAT('%',#{q.text},'%'))</if><if"
                    + " test='q.status != null and q.status != &quot;&quot;'> AND"
                    + " status=#{q.status}</if> ORDER BY <choose><when test=\"q.sort =="
                    + " 'latency_ms'\">latency_ms</when><when test=\"q.sort =="
                    + " 'status'\">status</when><otherwise>created_at</otherwise></choose>"
                    + " <choose><when test=\"q.direction =="
                    + " 'asc'\">ASC</when><otherwise>DESC</otherwise></choose>,sql_audit_id DESC LIMIT"
                    + " #{q.limit} OFFSET #{q.offset}</script>")
    List<SqlAuditRow> sqlAudits(@Param("q") Query query);

    @Select(
            "<script>SELECT COUNT(*) FROM sql_query_audits WHERE is_deleted=FALSE<if test='q.text"
                    + " != null and q.text != &quot;&quot;'> AND (trace_id ILIKE"
                    + " CONCAT('%',#{q.text},'%') OR status ILIKE CONCAT('%',#{q.text},'%'))</if><if"
                    + " test='q.status != null and q.status != &quot;&quot;'> AND"
                    + " status=#{q.status}</if></script>")
    long countSqlAudits(@Param("q") Query query);

    @Select(
            "<script>SELECT name,COALESCE(current_version,'-') AS version,risk_level AS"
                    + " risk,status,availability_scope AS scope,category AS"
                    + " owner,COALESCE(updated_at::text,'-') AS last_called_at FROM tool_registries"
                    + " WHERE is_deleted=FALSE<if test='q.text != null and q.text != &quot;&quot;'> AND"
                    + " (name ILIKE CONCAT('%',#{q.text},'%') OR category ILIKE"
                    + " CONCAT('%',#{q.text},'%'))</if><if test='q.status != null and q.status !="
                    + " &quot;&quot;'> AND status=#{q.status}</if> ORDER BY <choose><when test=\"q.sort"
                    + " == 'updated_at'\">updated_at</when><when test=\"q.sort =="
                    + " 'status'\">status</when><otherwise>name</otherwise></choose> <choose><when"
                    + " test=\"q.direction =="
                    + " 'asc'\">ASC</when><otherwise>DESC</otherwise></choose>,name ASC LIMIT"
                    + " #{q.limit} OFFSET #{q.offset}</script>")
    List<ToolRow> tools(@Param("q") Query query);

    @Select(
            "<script>SELECT COUNT(*) FROM tool_registries WHERE is_deleted=FALSE<if test='q.text !="
                    + " null and q.text != &quot;&quot;'> AND (name ILIKE CONCAT('%',#{q.text},'%') OR"
                    + " category ILIKE CONCAT('%',#{q.text},'%'))</if><if test='q.status != null and"
                    + " q.status != &quot;&quot;'> AND status=#{q.status}</if></script>")
    long countTools(@Param("q") Query query);

    @Select(
            "<script>SELECT provider_code AS provider,model_name AS"
                    + " model,scene,COALESCE((usage_json-&gt;&gt;'total_tokens'),'0') AS"
                    + " tokens,cost_amount AS cost,latency_ms,status FROM model_usage_logs WHERE"
                    + " is_deleted=FALSE<if test='q.text != null and q.text != &quot;&quot;'> AND"
                    + " (provider_code ILIKE CONCAT('%',#{q.text},'%') OR model_name ILIKE"
                    + " CONCAT('%',#{q.text},'%') OR scene ILIKE CONCAT('%',#{q.text},'%'))</if><if"
                    + " test='q.status != null and q.status != &quot;&quot;'> AND"
                    + " status=#{q.status}</if> ORDER BY <choose><when test=\"q.sort =="
                    + " 'latency_ms'\">latency_ms</when><when test=\"q.sort =="
                    + " 'status'\">status</when><otherwise>created_at</otherwise></choose>"
                    + " <choose><when test=\"q.direction =="
                    + " 'asc'\">ASC</when><otherwise>DESC</otherwise></choose>,model_usage_log_id DESC"
                    + " LIMIT #{q.limit} OFFSET #{q.offset}</script>")
    List<UsageRow> usage(@Param("q") Query query);

    @Select(
            "<script>SELECT provider_code AS provider,model_name AS"
                    + " model,scene,COALESCE((usage_json-&gt;&gt;'total_tokens'),'0') AS"
                    + " tokens,cost_amount AS cost,latency_ms,status FROM model_usage_logs WHERE"
                    + " is_deleted=FALSE<if test='q.text != null and q.text != &quot;&quot;'> AND"
                    + " (provider_code ILIKE CONCAT('%',#{q.text},'%') OR model_name ILIKE"
                    + " CONCAT('%',#{q.text},'%') OR scene ILIKE CONCAT('%',#{q.text},'%'))</if><if"
                    + " test='q.status != null and q.status != &quot;&quot;'> AND"
                    + " status=#{q.status}</if></script>")
    long countUsage(@Param("q") Query query);

    @Select(
            "<script>SELECT document_id,title,status,visibility,(SELECT COUNT(*) FROM"
                    + " knowledge_chunks c WHERE c.document_id=d.document_id AND c.is_deleted=FALSE) AS"
                    + " chunks,COALESCE(source_name,source_type,'-') AS source,CASE WHEN"
                    + " status='indexed' THEN '100%' WHEN status='parsed' THEN '70%' ELSE '0%' END AS"
                    + " index_progress,updated_at FROM knowledge_documents d WHERE is_deleted=FALSE<if"
                    + " test='q.text != null and q.text != &quot;&quot;'> AND (title ILIKE"
                    + " CONCAT('%',#{q.text},'%') OR source_name ILIKE CONCAT('%',#{q.text},'%') OR"
                    + " source_type ILIKE CONCAT('%',#{q.text},'%'))</if><if test='q.status != null and"
                    + " q.status != &quot;&quot;'> AND status=#{q.status}</if><if test='q.visibility !="
                    + " null and q.visibility != &quot;&quot;'> AND visibility=#{q.visibility}</if>"
                    + " ORDER BY <choose><when test=\"q.sort == 'title'\">title</when><when"
                    + " test=\"q.sort =="
                    + " 'status'\">status</when><otherwise>updated_at</otherwise></choose>"
                    + " <choose><when test=\"q.direction =="
                    + " 'asc'\">ASC</when><otherwise>DESC</otherwise></choose>,document_id DESC LIMIT"
                    + " #{q.limit} OFFSET #{q.offset}</script>")
    List<KnowledgeRow> knowledge(@Param("q") Query query);

    @Select(
            "<script>SELECT COUNT(*) FROM knowledge_documents WHERE is_deleted=FALSE<if"
                    + " test='q.text != null and q.text != &quot;&quot;'> AND (title ILIKE"
                    + " CONCAT('%',#{q.text},'%') OR source_name ILIKE CONCAT('%',#{q.text},'%') OR"
                    + " source_type ILIKE CONCAT('%',#{q.text},'%'))</if><if test='q.status != null and"
                    + " q.status != &quot;&quot;'> AND status=#{q.status}</if><if test='q.visibility !="
                    + " null and q.visibility != &quot;&quot;'> AND"
                    + " visibility=#{q.visibility}</if></script>")
    long countKnowledge(@Param("q") Query query);

    @Select(
            "<script>SELECT resource_type,resource_id,owner_ref,deleted_at,reason FROM (SELECT"
                    + " 'user' AS resource_type,user_id AS resource_id,CONCAT('user-',MD5(CAST(user_id"
                    + " AS TEXT))) AS owner_ref,deleted_at,'account_deleted' AS reason FROM users WHERE"
                    + " is_deleted=TRUE UNION ALL SELECT"
                    + " 'knowledge_document',document_id,CONCAT('user-',MD5(CAST(COALESCE(created_by,0)"
                    + " AS TEXT))),deleted_at,'knowledge_document_deleted' FROM knowledge_documents"
                    + " WHERE is_deleted=TRUE UNION ALL SELECT"
                    + " 'food_log',food_log_id,CONCAT('user-',MD5(CAST(user_id AS"
                    + " TEXT))),deleted_at,'food_log_deleted' FROM food_logs WHERE is_deleted=TRUE"
                    + " UNION ALL SELECT 'meal_plan',meal_plan_id,CONCAT('user-',MD5(CAST(user_id AS"
                    + " TEXT))),deleted_at,'meal_plan_deleted' FROM meal_plans WHERE is_deleted=TRUE"
                    + " UNION ALL SELECT 'message',message_id,CONCAT('user-',MD5(CAST(created_by AS"
                    + " TEXT))),deleted_at,'message_deleted' FROM messages WHERE is_deleted=TRUE)"
                    + " deleted_resources WHERE 1=1<if test='q.text != null and q.text !="
                    + " &quot;&quot;'> AND (resource_type ILIKE CONCAT('%',#{q.text},'%') OR owner_ref"
                    + " ILIKE CONCAT('%',#{q.text},'%'))</if> ORDER BY <choose><when test=\"q.sort =="
                    + " 'resource_type'\">resource_type</when><otherwise>deleted_at</otherwise></choose>"
                    + " <choose><when test=\"q.direction =="
                    + " 'asc'\">ASC</when><otherwise>DESC</otherwise></choose>,resource_id DESC LIMIT"
                    + " #{q.limit} OFFSET #{q.offset}</script>")
    List<DeletedRow> deleted(@Param("q") Query query);

    @Select(
            "<script>SELECT COUNT(*) FROM (SELECT user_id AS"
                    + " resource_id,CONCAT('user-',MD5(CAST(user_id AS TEXT))) AS owner_ref,'user' AS"
                    + " resource_type FROM users WHERE is_deleted=TRUE UNION ALL SELECT"
                    + " document_id,CONCAT('user-',MD5(CAST(COALESCE(created_by,0) AS"
                    + " TEXT))),'knowledge_document' FROM knowledge_documents WHERE is_deleted=TRUE"
                    + " UNION ALL SELECT food_log_id,CONCAT('user-',MD5(CAST(user_id AS"
                    + " TEXT))),'food_log' FROM food_logs WHERE is_deleted=TRUE UNION ALL SELECT"
                    + " meal_plan_id,CONCAT('user-',MD5(CAST(user_id AS TEXT))),'meal_plan' FROM"
                    + " meal_plans WHERE is_deleted=TRUE UNION ALL SELECT"
                    + " message_id,CONCAT('user-',MD5(CAST(created_by AS TEXT))),'message' FROM"
                    + " messages WHERE is_deleted=TRUE) deleted_resources WHERE 1=1<if test='q.text !="
                    + " null and q.text != &quot;&quot;'> AND (resource_type ILIKE"
                    + " CONCAT('%',#{q.text},'%') OR owner_ref ILIKE"
                    + " CONCAT('%',#{q.text},'%'))</if></script>")
    long countDeleted(@Param("q") Query query);

    @Select(
            "<script>SELECT"
                    + " operator_id,action,target_type,target_id,result,request_id,trace_id,created_at"
                    + " FROM operation_audits WHERE is_deleted=FALSE<if test='q.text != null and q.text"
                    + " != &quot;&quot;'> AND (action ILIKE CONCAT('%',#{q.text},'%') OR target_type"
                    + " ILIKE CONCAT('%',#{q.text},'%') OR target_id ILIKE"
                    + " CONCAT('%',#{q.text},'%'))</if><if test='q.status != null and q.status !="
                    + " &quot;&quot;'> AND result=#{q.status}</if> ORDER BY <choose><when test=\"q.sort"
                    + " == 'result'\">result</when><when test=\"q.sort =="
                    + " 'action'\">action</when><otherwise>created_at</otherwise></choose>"
                    + " <choose><when test=\"q.direction =="
                    + " 'asc'\">ASC</when><otherwise>DESC</otherwise></choose>,operation_audit_id DESC"
                    + " LIMIT #{q.limit} OFFSET #{q.offset}</script>")
    List<OperationAuditRow> operationAudits(@Param("q") Query query);

    @Select(
            "<script>SELECT COUNT(*) FROM operation_audits WHERE is_deleted=FALSE<if test='q.text"
                    + " != null and q.text != &quot;&quot;'> AND (action ILIKE"
                    + " CONCAT('%',#{q.text},'%') OR target_type ILIKE CONCAT('%',#{q.text},'%') OR"
                    + " target_id ILIKE CONCAT('%',#{q.text},'%'))</if><if test='q.status != null and"
                    + " q.status != &quot;&quot;'> AND result=#{q.status}</if></script>")
    long countOperationAudits(@Param("q") Query query);

    @Select(
            "<script>SELECT dlq_id,consumer_group,source_topic,mq_message_id AS message_id,run_id,"
                    + "dispatch_id,event_id,attempt,reconsume_times,error_code,reconciliation_state,"
                    + "first_seen_at,reconciled_at FROM runtime_message_dlq WHERE 1=1<if test='q.text != null"
                    + " and q.text != &quot;&quot;'> AND (consumer_group ILIKE CONCAT('%',#{q.text},'%') OR"
                    + " source_topic ILIKE CONCAT('%',#{q.text},'%') OR run_id ILIKE CONCAT('%',#{q.text},'%')"
                    + " OR error_code ILIKE CONCAT('%',#{q.text},'%'))</if><if test='q.status != null and"
                    + " q.status != &quot;&quot;'> AND reconciliation_state=#{q.status}</if> ORDER BY <choose>"
                    + "<when test=\"q.sort == 'reconciled_at'\">reconciled_at</when><when test=\"q.sort =="
                    + " 'reconsume_times'\">reconsume_times</when><when test=\"q.sort == 'state'\">"
                    + "reconciliation_state</when><otherwise>first_seen_at</otherwise></choose> <choose><when"
                    + " test=\"q.direction == 'asc'\">ASC</when><otherwise>DESC</otherwise></choose>,dlq_id DESC"
                    + " LIMIT #{q.limit} OFFSET #{q.offset}</script>")
    List<DlqRow> dlq(@Param("q") Query query);

    @Select(
            "<script>SELECT COUNT(*) FROM runtime_message_dlq WHERE 1=1<if test='q.text != null and"
                    + " q.text != &quot;&quot;'> AND (consumer_group ILIKE CONCAT('%',#{q.text},'%') OR"
                    + " source_topic ILIKE CONCAT('%',#{q.text},'%') OR run_id ILIKE CONCAT('%',#{q.text},'%')"
                    + " OR error_code ILIKE CONCAT('%',#{q.text},'%'))</if><if test='q.status != null and"
                    + " q.status != &quot;&quot;'> AND reconciliation_state=#{q.status}</if></script>")
    long countDlq(@Param("q") Query query);
}
