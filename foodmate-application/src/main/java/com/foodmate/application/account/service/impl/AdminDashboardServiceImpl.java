package com.foodmate.application.account.service.impl;

import com.foodmate.application.account.port.out.AdminDashboardRepository;
import com.foodmate.application.account.service.AdminDashboardService;
import com.foodmate.application.account.service.AdminDashboardService.DashboardView;
import com.foodmate.application.account.service.AdminDashboardService.DeletedResource;
import com.foodmate.application.account.service.AdminDashboardService.Knowledge;
import com.foodmate.application.account.service.AdminDashboardService.Metric;
import com.foodmate.application.account.service.AdminDashboardService.OperationAudit;
import com.foodmate.application.account.service.AdminDashboardService.Run;
import com.foodmate.application.account.service.AdminDashboardService.SqlAudit;
import com.foodmate.application.account.service.AdminDashboardService.Tool;
import com.foodmate.application.account.service.AdminDashboardService.ToolCall;
import com.foodmate.application.account.service.AdminDashboardService.Usage;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class AdminDashboardServiceImpl implements AdminDashboardService {
    private final AdminDashboardRepository store;

    public AdminDashboardServiceImpl(AdminDashboardRepository store) {
        this.store = store;
    }

    public DashboardView dashboard() {
        AdminDashboardRepository.Overview overview = store.overview();
        return new DashboardView(
                List.of(
                        new Metric(
                                "AgentRun 今日", Long.toString(overview.runsToday()), "实时", "green"),
                        new Metric("失败率", overview.failureRate() + "%", "全部记录", "danger"),
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

    private static Run run(AdminDashboardRepository.RunRow row) {
        return new Run(
                row.agentRunId(),
                row.sessionId(),
                row.intent(),
                row.status(),
                row.traceId(),
                row.durationMs(),
                row.username());
    }

    private static ToolCall toolCall(AdminDashboardRepository.ToolCallRow row) {
        return new ToolCall(
                row.toolCallId(),
                row.agentRunId(),
                row.toolName(),
                row.status(),
                row.latencyMs(),
                row.traceId());
    }

    private static SqlAudit sqlAudit(AdminDashboardRepository.SqlAuditRow row) {
        return new SqlAudit(
                row.sqlAuditId(), row.actor(), row.statement(), row.result(), row.traceId());
    }

    private static Tool tool(AdminDashboardRepository.ToolRow row) {
        return new Tool(
                row.name(),
                row.version(),
                row.risk(),
                row.status(),
                row.scope(),
                row.owner(),
                row.lastCalledAt(),
                row.revision());
    }

    private static Usage usage(AdminDashboardRepository.UsageRow row) {
        return new Usage(
                row.provider(),
                row.model(),
                row.scene(),
                row.tokens(),
                row.cost(),
                row.latencyMs(),
                row.status());
    }

    private static Knowledge knowledge(AdminDashboardRepository.KnowledgeRow row) {
        return new Knowledge(
                row.documentId(),
                row.title(),
                row.status(),
                row.visibility(),
                row.chunks(),
                row.owner(),
                row.source(),
                row.indexProgress(),
                row.updatedAt());
    }

    private static DeletedResource deleted(AdminDashboardRepository.DeletedRow row) {
        return new DeletedResource(
                row.resourceType(), row.resourceId(), row.owner(), row.deletedAt(), row.reason());
    }

    private static OperationAudit operationAudit(AdminDashboardRepository.OperationAuditRow row) {
        return new OperationAudit(
                row.operatorId(),
                row.action(),
                row.targetType(),
                row.targetId(),
                row.result(),
                row.requestId(),
                row.traceId(),
                row.createdAt());
    }
}
