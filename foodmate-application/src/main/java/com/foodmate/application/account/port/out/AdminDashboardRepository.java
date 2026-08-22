package com.foodmate.application.account.port.out;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public interface AdminDashboardRepository {
    Overview overview();

    long modelUsageCount();

    long knowledgeCount();

    List<RunRow> runs();

    List<ToolCallRow> toolCalls();

    List<SqlAuditRow> sqlAudits();

    List<ToolRow> tools();

    List<UsageRow> usage();

    List<KnowledgeRow> knowledge();

    List<DeletedRow> deleted();

    List<OperationAuditRow> operationAudits();

    record Overview(long runsToday, BigDecimal failureRate) {}

    record RunRow(
            Long agentRunId,
            Long sessionId,
            String intent,
            String status,
            String traceId,
            BigDecimal durationMs,
            String username) {}

    record ToolCallRow(
            Long toolCallId,
            Long agentRunId,
            String toolName,
            String status,
            Long latencyMs,
            String traceId) {}

    record SqlAuditRow(
            Long sqlAuditId, Long actor, String statement, String result, String traceId) {}

    record ToolRow(
            String name,
            String version,
            String risk,
            String status,
            String scope,
            String owner,
            String lastCalledAt) {}

    record UsageRow(
            String provider,
            String model,
            String scene,
            String tokens,
            BigDecimal cost,
            Long latencyMs,
            String status) {}

    record KnowledgeRow(
            Long documentId,
            String title,
            String status,
            String visibility,
            Long chunks,
            String owner,
            String source,
            String indexProgress,
            Instant updatedAt) {}

    record DeletedRow(
            String resourceType, Long resourceId, String owner, Instant deletedAt, String reason) {}

    record OperationAuditRow(
            Long operatorId,
            String action,
            String targetType,
            String targetId,
            String result,
            String requestId,
            String traceId,
            Instant createdAt) {}
}
