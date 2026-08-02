package com.foodmate.application.account.service.impl;

import com.foodmate.application.account.port.out.AdminDashboardRepository;
import com.foodmate.application.account.service.AdminDashboardService;
import com.foodmate.application.account.service.AdminDashboardService.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class AdminDashboardServiceImpl implements AdminDashboardService {
    private final AdminDashboardRepository store;

    public AdminDashboardServiceImpl(AdminDashboardRepository store) {
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
                store.runs().stream().map(AdminDashboardServiceImpl::run).toList(),
                store.toolCalls().stream().map(AdminDashboardServiceImpl::toolCall).toList(),
                store.sqlAudits().stream().map(AdminDashboardServiceImpl::sqlAudit).toList(),
                store.tools().stream().map(AdminDashboardServiceImpl::tool).toList(),
                store.usage().stream().map(AdminDashboardServiceImpl::usage).toList(),
                store.knowledge().stream().map(AdminDashboardServiceImpl::knowledge).toList(),
                store.deleted().stream().map(AdminDashboardServiceImpl::deleted).toList(),
                store.operationAudits().stream()
                        .map(AdminDashboardServiceImpl::operationAudit)
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
}
