package com.foodmate.application.account.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/** 后台仪表盘查询用例接口。 */
public interface AdminDashboardService {
    DashboardView dashboard();

    record DashboardView(
            List<Metric> overviewMetrics,
            List<Run> runs,
            List<ToolCall> toolCalls,
            List<SqlAudit> sqlAudits,
            List<Tool> tools,
            List<Usage> usage,
            List<Knowledge> knowledge,
            List<DeletedResource> deleted,
            List<OperationAudit> operationAudits) {}

    record Metric(String label, String value, String hint, String tone) {}

    record Run(
            Long agentRunId,
            Long sessionId,
            String intent,
            String status,
            String traceId,
            BigDecimal durationMs,
            String username) {}

    record ToolCall(
            Long toolCallId,
            Long agentRunId,
            String toolName,
            String status,
            Long latencyMs,
            String traceId) {}

    record SqlAudit(Long sqlAuditId, Long actor, String statement, String result, String traceId) {}

    record Tool(
            String name,
            String version,
            String risk,
            String status,
            String scope,
            String owner,
            String lastCalledAt,
            long revision) {}

    record Usage(
            String provider,
            String model,
            String scene,
            String tokens,
            BigDecimal cost,
            Long latencyMs,
            String status) {}

    record Knowledge(
            Long documentId,
            String title,
            String status,
            String visibility,
            Long chunks,
            String owner,
            String source,
            String indexProgress,
            Instant updatedAt) {}

    record DeletedResource(
            String resourceType, Long resourceId, String owner, Instant deletedAt, String reason) {}

    record OperationAudit(
            Long operatorId,
            String action,
            String targetType,
            String targetId,
            String result,
            String requestId,
            String traceId,
            Instant createdAt) {}
}
