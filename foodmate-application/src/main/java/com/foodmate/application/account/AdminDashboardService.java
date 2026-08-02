package com.foodmate.application.account;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class AdminDashboardService {
    private final AdminDashboardStore store;

    public AdminDashboardService(AdminDashboardStore store) {
        this.store = store;
    }

    public DashboardView dashboard() {
        Map<String, Object> row = store.overview();
        return new DashboardView(
                List.of(
                        new Metric("AgentRun 今日", text(row.get("runs_today")), "实时", "green"),
                        new Metric("失败率", text(row.get("failure_rate")) + "%", "全部记录", "danger"),
                        new Metric("模型调用", Long.toString(store.modelUsageCount()), "累计", "orange"),
                        new Metric("知识文档", Long.toString(store.knowledgeCount()), "实际记录", "blue")),
                store.runs().stream().map(AdminDashboardService::run).toList(),
                store.toolCalls().stream().map(AdminDashboardService::toolCall).toList(),
                store.sqlAudits().stream().map(AdminDashboardService::sqlAudit).toList(),
                store.tools().stream().map(AdminDashboardService::tool).toList(),
                store.usage().stream().map(AdminDashboardService::usage).toList(),
                store.knowledge().stream().map(AdminDashboardService::knowledge).toList(),
                store.deleted().stream().map(AdminDashboardService::deleted).toList(),
                store.operationAudits().stream()
                        .map(AdminDashboardService::operationAudit)
                        .toList());
    }

    private static Run run(Map<String, Object> row) {
        return new Run(
                number(row.get("agent_run_id")),
                number(row.get("session_id")),
                text(row.get("intent")),
                text(row.get("status")),
                text(row.get("trace_id")),
                decimal(row.get("duration_ms")),
                text(row.get("username")));
    }

    private static ToolCall toolCall(Map<String, Object> row) {
        return new ToolCall(
                number(row.get("tool_call_id")),
                number(row.get("agent_run_id")),
                text(row.get("tool_name")),
                text(row.get("status")),
                number(row.get("latency_ms")),
                text(row.get("trace_id")));
    }

    private static SqlAudit sqlAudit(Map<String, Object> row) {
        return new SqlAudit(
                number(row.get("sql_audit_id")),
                number(row.get("actor")),
                text(row.get("statement")),
                text(row.get("result")),
                text(row.get("trace_id")));
    }

    private static Tool tool(Map<String, Object> row) {
        return new Tool(
                text(row.get("name")),
                text(row.get("version")),
                text(row.get("risk")),
                text(row.get("status")),
                text(row.get("scope")),
                text(row.get("owner")),
                text(row.get("last_called_at")));
    }

    private static Usage usage(Map<String, Object> row) {
        return new Usage(
                text(row.get("provider")),
                text(row.get("model")),
                text(row.get("scene")),
                text(row.get("tokens")),
                decimal(row.get("cost")),
                number(row.get("latency_ms")),
                text(row.get("status")));
    }

    private static Knowledge knowledge(Map<String, Object> row) {
        return new Knowledge(
                number(row.get("document_id")),
                text(row.get("title")),
                text(row.get("status")),
                number(row.get("chunks")),
                text(row.get("owner")),
                text(row.get("source")),
                text(row.get("index_progress")),
                instant(row.get("updated_at")));
    }

    private static DeletedResource deleted(Map<String, Object> row) {
        return new DeletedResource(
                text(row.get("resource_type")),
                number(row.get("resource_id")),
                text(row.get("owner")),
                instant(row.get("deleted_at")),
                text(row.get("reason")));
    }

    private static OperationAudit operationAudit(Map<String, Object> row) {
        return new OperationAudit(
                number(row.get("operator_id")),
                text(row.get("action")),
                text(row.get("target_type")),
                text(row.get("target_id")),
                text(row.get("result")),
                text(row.get("request_id")),
                text(row.get("trace_id")),
                instant(row.get("created_at")));
    }

    private static String text(Object value) {
        return value == null ? null : value.toString();
    }

    private static Long number(Object value) {
        if (value instanceof Number number) return number.longValue();
        try {
            return value == null ? null : Long.valueOf(value.toString());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static BigDecimal decimal(Object value) {
        try {
            return value == null ? null : new BigDecimal(value.toString());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static Instant instant(Object value) {
        if (value instanceof Instant instant) return instant;
        if (value instanceof java.sql.Timestamp timestamp) return timestamp.toInstant();
        try {
            return value == null ? null : Instant.parse(value.toString());
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    public record DashboardView(
            List<Metric> overviewMetrics,
            List<Run> runs,
            List<ToolCall> toolCalls,
            List<SqlAudit> sqlAudits,
            List<Tool> tools,
            List<Usage> usage,
            List<Knowledge> knowledge,
            List<DeletedResource> deleted,
            List<OperationAudit> operationAudits) {}

    public record Metric(String label, String value, String hint, String tone) {}

    public record Run(
            Long agentRunId,
            Long sessionId,
            String intent,
            String status,
            String traceId,
            BigDecimal durationMs,
            String username) {}

    public record ToolCall(
            Long toolCallId,
            Long agentRunId,
            String toolName,
            String status,
            Long latencyMs,
            String traceId) {}

    public record SqlAudit(
            Long sqlAuditId, Long actor, String statement, String result, String traceId) {}

    public record Tool(
            String name,
            String version,
            String risk,
            String status,
            String scope,
            String owner,
            String lastCalledAt) {}

    public record Usage(
            String provider,
            String model,
            String scene,
            String tokens,
            BigDecimal cost,
            Long latencyMs,
            String status) {}

    public record Knowledge(
            Long documentId,
            String title,
            String status,
            Long chunks,
            String owner,
            String source,
            String indexProgress,
            Instant updatedAt) {}

    public record DeletedResource(
            String resourceType, Long resourceId, String owner, Instant deletedAt, String reason) {}

    public record OperationAudit(
            Long operatorId,
            String action,
            String targetType,
            String targetId,
            String result,
            String requestId,
            String traceId,
            Instant createdAt) {}
}
