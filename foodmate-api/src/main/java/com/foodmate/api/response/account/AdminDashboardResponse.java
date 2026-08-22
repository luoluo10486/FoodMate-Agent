package com.foodmate.api.response.account;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.foodmate.application.account.service.AdminDashboardService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record AdminDashboardResponse(
        List<Metric> overviewMetrics,
        List<Run> runs,
        List<ToolCall> toolCalls,
        List<SqlAudit> sqlAudits,
        List<Tool> tools,
        List<Usage> usage,
        List<Knowledge> knowledge,
        List<DeletedResource> deleted,
        List<OperationAudit> operationAudits) {
    public static AdminDashboardResponse from(AdminDashboardService.DashboardView value) {
        return new AdminDashboardResponse(
                value.overviewMetrics().stream()
                        .map(
                                item ->
                                        new Metric(
                                                item.label(),
                                                item.value(),
                                                item.hint(),
                                                item.tone()))
                        .toList(),
                value.runs().stream()
                        .map(
                                item ->
                                        new Run(
                                                item.agentRunId(),
                                                item.sessionId(),
                                                item.intent(),
                                                item.status(),
                                                item.traceId(),
                                                item.durationMs(),
                                                item.username()))
                        .toList(),
                value.toolCalls().stream()
                        .map(
                                item ->
                                        new ToolCall(
                                                item.toolCallId(),
                                                item.agentRunId(),
                                                item.toolName(),
                                                item.status(),
                                                item.latencyMs(),
                                                item.traceId()))
                        .toList(),
                value.sqlAudits().stream()
                        .map(
                                item ->
                                        new SqlAudit(
                                                item.sqlAuditId(),
                                                item.actor(),
                                                item.statement(),
                                                item.result(),
                                                item.traceId()))
                        .toList(),
                value.tools().stream()
                        .map(
                                item ->
                                        new Tool(
                                                item.name(),
                                                item.version(),
                                                item.risk(),
                                                item.status(),
                                                item.scope(),
                                                item.owner(),
                                                item.lastCalledAt()))
                        .toList(),
                value.usage().stream()
                        .map(
                                item ->
                                        new Usage(
                                                item.provider(),
                                                item.model(),
                                                item.scene(),
                                                item.tokens(),
                                                item.cost(),
                                                item.latencyMs(),
                                                item.status()))
                        .toList(),
                value.knowledge().stream()
                        .map(
                                item ->
                                        new Knowledge(
                                                item.documentId(),
                                                item.title(),
                                                item.status(),
                                                item.visibility(),
                                                item.chunks(),
                                                item.owner(),
                                                item.source(),
                                                item.indexProgress(),
                                                item.updatedAt()))
                        .toList(),
                value.deleted().stream()
                        .map(
                                item ->
                                        new DeletedResource(
                                                item.resourceType(),
                                                item.resourceId(),
                                                item.owner(),
                                                item.deletedAt(),
                                                item.reason()))
                        .toList(),
                value.operationAudits().stream()
                        .map(
                                item ->
                                        new OperationAudit(
                                                item.operatorId(),
                                                item.action(),
                                                item.targetType(),
                                                item.targetId(),
                                                item.result(),
                                                item.requestId(),
                                                item.traceId(),
                                                item.createdAt()))
                        .toList());
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Metric(String label, String value, String hint, String tone) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Run(
            Long agentRunId,
            Long sessionId,
            String intent,
            String status,
            String traceId,
            BigDecimal durationMs,
            String username) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ToolCall(
            Long toolCallId,
            Long agentRunId,
            String toolName,
            String status,
            Long latencyMs,
            String traceId) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record SqlAudit(
            Long sqlAuditId, Long actor, String statement, String result, String traceId) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Tool(
            String name,
            String version,
            String risk,
            String status,
            String scope,
            String owner,
            String lastCalledAt) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Usage(
            String provider,
            String model,
            String scene,
            String tokens,
            BigDecimal cost,
            Long latencyMs,
            String status) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Knowledge(
            Long documentId,
            String title,
            String status,
            String visibility,
            Long chunks,
            String owner,
            String source,
            String indexProgress,
            Instant updatedAt) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record DeletedResource(
            String resourceType, Long resourceId, String owner, Instant deletedAt, String reason) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
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
