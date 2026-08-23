package com.foodmate.application.account.port.out;

import java.time.Instant;

/** 只读运营审计报告的数据端口；返回聚合值，不返回业务载荷。 */
public interface AdminAuditReportRepository {
    OperationAuditSummary operationAudits();

    OutboxSummary runtimeDispatchOutbox(Instant staleBefore);

    OutboxSummary knowledgeIndexOutbox(Instant staleBefore);

    OutboxSummary knowledgeVisibilityOutbox(Instant staleBefore);

    OutboxSummary agentRunSseOutbox(Instant staleBefore);

    KnowledgeImportSummary knowledgeImports();

    DlqSummary dlq();

    record OperationAuditSummary(long pendingCount, long failedCount, Instant oldestPendingAt) {}

    record OutboxSummary(long staleCount, long failedCount, Instant oldestStaleAt) {}

    record KnowledgeImportSummary(long pendingCount, long failedCount, Instant oldestFailureAt) {}

    record DlqSummary(long pendingCount, long needsAttentionCount, Instant oldestPendingAt) {}
}
