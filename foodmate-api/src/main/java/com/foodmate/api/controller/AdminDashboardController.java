package com.foodmate.api.controller;

import com.foodmate.application.account.UserAccountService;
import com.foodmate.shared.api.ApiResponse;
import com.foodmate.shared.trace.TraceContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/dashboard")
public class AdminDashboardController extends AuthenticatedControllerSupport {
    private final JdbcTemplate jdbc;

    public AdminDashboardController(UserAccountService accounts, ObjectProvider<JdbcTemplate> jdbc) {
        super(accounts);
        this.jdbc = jdbc.getIfAvailable();
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> dashboard(HttpServletRequest request) {
        requireAnyRole(request, "admin", "operator", "superadmin");
        if (jdbc == null) return ApiResponse.success(empty(), TraceContextHolder.currentOrNew());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("overview_metrics", overview());
        result.put("runs", runs());
        result.put("tool_calls", toolCalls());
        result.put("sql_audits", sqlAudits());
        result.put("tools", tools());
        result.put("usage", usage());
        result.put("knowledge", knowledge());
        result.put("deleted", deleted());
        result.put("operation_audits", operationAudits());
        return ApiResponse.success(result, TraceContextHolder.currentOrNew());
    }

    private List<Map<String, Object>> overview() {
        Map<String, Object> row = jdbc.queryForMap("SELECT COUNT(*) FILTER (WHERE created_at >= CURRENT_DATE) AS runs_today, COALESCE(ROUND(100.0 * COUNT(*) FILTER (WHERE status='failed') / NULLIF(COUNT(*),0),1),0) AS failure_rate, COUNT(*) AS total_runs FROM agent_runs WHERE is_deleted=FALSE");
        return List.of(Map.of("label", "AgentRun 今日", "value", String.valueOf(row.get("runs_today")), "hint", "实时", "tone", "green"), Map.of("label", "失败率", "value", row.get("failure_rate") + "%", "hint", "全部记录", "tone", "danger"), Map.of("label", "模型调用", "value", String.valueOf(jdbc.queryForObject("SELECT COUNT(*) FROM model_usage_logs WHERE is_deleted=FALSE", Long.class)), "hint", "累计", "tone", "orange"), Map.of("label", "知识文档", "value", String.valueOf(jdbc.queryForObject("SELECT COUNT(*) FROM knowledge_documents WHERE is_deleted=FALSE", Long.class)), "hint", "实际记录", "tone", "blue"));
    }

    private List<Map<String, Object>> runs() { return jdbc.queryForList("SELECT r.agent_run_id, r.session_id, r.intent, r.status, r.trace_id, EXTRACT(EPOCH FROM (r.updated_at-r.created_at))*1000 AS duration_ms, u.username FROM agent_runs r JOIN sessions s ON s.session_id=r.session_id LEFT JOIN users u ON u.user_id=s.user_id WHERE r.is_deleted=FALSE ORDER BY r.created_at DESC LIMIT 100"); }
    private List<Map<String, Object>> toolCalls() { return jdbc.queryForList("SELECT tool_call_id, agent_run_id, tool_name, status, latency_ms, trace_id FROM tool_calls WHERE is_deleted=FALSE ORDER BY created_at DESC LIMIT 100"); }
    private List<Map<String, Object>> sqlAudits() { return jdbc.queryForList("SELECT sql_audit_id, created_by AS actor, LEFT(COALESCE(sql_text, original_question), 200) AS statement, status AS result, trace_id FROM sql_query_audits WHERE is_deleted=FALSE ORDER BY created_at DESC LIMIT 100"); }
    private List<Map<String, Object>> tools() { return jdbc.queryForList("SELECT name, COALESCE(current_version,'-') AS version, risk_level AS risk, status, availability_scope AS scope, category AS owner, COALESCE(updated_at::text,'-') AS last_called_at FROM tool_registries WHERE is_deleted=FALSE ORDER BY name"); }
    private List<Map<String, Object>> usage() { return jdbc.queryForList("SELECT provider_code AS provider, model_name AS model, scene, COALESCE((usage_json->>'total_tokens'),'0') AS tokens, cost_amount AS cost, latency_ms, status FROM model_usage_logs WHERE is_deleted=FALSE ORDER BY created_at DESC LIMIT 100"); }
    private List<Map<String, Object>> knowledge() { return jdbc.queryForList("SELECT document_id, title, status, (SELECT COUNT(*) FROM knowledge_chunks c WHERE c.document_id=d.document_id AND c.is_deleted=FALSE) AS chunks, created_by AS owner, storage_key AS source, CASE WHEN status='indexed' THEN '100%' WHEN status='parsed' THEN '70%' ELSE '0%' END AS index_progress, updated_at FROM knowledge_documents d WHERE is_deleted=FALSE ORDER BY updated_at DESC LIMIT 100"); }
    private List<Map<String, Object>> deleted() { return jdbc.queryForList("SELECT 'user' AS resource_type, user_id AS resource_id, username AS owner, deleted_at, '账号注销' AS reason FROM users WHERE is_deleted=TRUE ORDER BY deleted_at DESC LIMIT 100"); }
    private List<Map<String, Object>> operationAudits() { return jdbc.queryForList("SELECT operator_id, action, target_type, target_id, result, request_id, trace_id, created_at FROM operation_audits WHERE is_deleted=FALSE ORDER BY created_at DESC LIMIT 100"); }
    private Map<String, Object> empty() { return Map.of("overview_metrics", List.of(), "runs", List.of(), "tool_calls", List.of(), "sql_audits", List.of(), "tools", List.of(), "usage", List.of(), "knowledge", List.of(), "deleted", List.of(), "operation_audits", List.of()); }
}
