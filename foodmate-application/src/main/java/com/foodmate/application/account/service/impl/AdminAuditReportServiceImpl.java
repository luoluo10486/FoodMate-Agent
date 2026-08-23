package com.foodmate.application.account.service.impl;

import com.foodmate.application.account.port.out.AdminAuditReportRepository;
import com.foodmate.application.account.port.out.AdminAuditReportRepository.DlqSummary;
import com.foodmate.application.account.port.out.AdminAuditReportRepository.KnowledgeImportSummary;
import com.foodmate.application.account.port.out.AdminAuditReportRepository.OperationAuditSummary;
import com.foodmate.application.account.port.out.AdminAuditReportRepository.OutboxSummary;
import com.foodmate.application.account.service.AdminAuditReportService;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** 汇总既有权威事实，供管理员判断是否需要人工处理。 */
@Service
public class AdminAuditReportServiceImpl implements AdminAuditReportService {
    private static final int DEFAULT_STALE_THRESHOLD_MINUTES = 15;
    private static final int MAX_STALE_THRESHOLD_MINUTES = 24 * 60;

    private final AdminAuditReportRepository store;
    private final int staleThresholdMinutes;

    /** Compatibility constructor for application-only tests. */
    public AdminAuditReportServiceImpl(AdminAuditReportRepository store) {
        this(store, DEFAULT_STALE_THRESHOLD_MINUTES);
    }

    @Autowired
    public AdminAuditReportServiceImpl(
            AdminAuditReportRepository store,
            @Value("${foodmate.admin.audit-report-stale-minutes:15}") int staleThresholdMinutes) {
        this.store = store;
        this.staleThresholdMinutes = normalizeThreshold(staleThresholdMinutes);
    }

    @Override
    public Report current() {
        Instant generatedAt = Instant.now();
        Instant staleBefore = generatedAt.minus(staleThresholdMinutes, ChronoUnit.MINUTES);
        List<Check> checks = new ArrayList<>();

        checks.add(operationAuditCheck(store.operationAudits()));
        checks.add(
                outboxCheck(
                        "RUNTIME_DISPATCH_OUTBOX",
                        store.runtimeDispatchOutbox(staleBefore),
                        "STALE_OUTBOX",
                        "FAILED_OUTBOX"));
        checks.add(
                outboxCheck(
                        "KNOWLEDGE_INDEX_OUTBOX",
                        store.knowledgeIndexOutbox(staleBefore),
                        "STALE_KNOWLEDGE_OUTBOX",
                        "FAILED_KNOWLEDGE_OUTBOX"));
        checks.add(
                outboxCheck(
                        "KNOWLEDGE_VISIBILITY_OUTBOX",
                        store.knowledgeVisibilityOutbox(staleBefore),
                        "STALE_VISIBILITY_OUTBOX",
                        "FAILED_VISIBILITY_OUTBOX"));
        checks.add(
                outboxCheck(
                        "AGENT_RUN_SSE_OUTBOX",
                        store.agentRunSseOutbox(staleBefore),
                        "STALE_SSE_OUTBOX",
                        "FAILED_SSE_OUTBOX"));
        checks.add(knowledgeImportCheck(store.knowledgeImports()));
        checks.add(dlqCheck(store.dlq()));

        String status =
                checks.stream().anyMatch(check -> "attention".equals(check.status()))
                        ? "attention"
                        : "passed";
        return new Report(generatedAt, staleThresholdMinutes, status, List.copyOf(checks));
    }

    private static Check operationAuditCheck(OperationAuditSummary summary) {
        List<String> reasons = new ArrayList<>();
        if (summary.pendingCount() > 0) reasons.add("PENDING_OPERATION_AUDIT");
        if (summary.failedCount() > 0) reasons.add("FAILED_OPERATION_AUDIT");
        return check(
                "OPERATION_AUDIT",
                summary.pendingCount(),
                summary.failedCount(),
                summary.oldestPendingAt(),
                reasons);
    }

    private static Check outboxCheck(
            String code, OutboxSummary summary, String staleReason, String failedReason) {
        List<String> reasons = new ArrayList<>();
        if (summary.staleCount() > 0) reasons.add(staleReason);
        if (summary.failedCount() > 0) reasons.add(failedReason);
        return check(
                code,
                summary.staleCount(),
                summary.failedCount(),
                summary.oldestStaleAt(),
                reasons);
    }

    private static Check knowledgeImportCheck(KnowledgeImportSummary summary) {
        List<String> reasons = new ArrayList<>();
        if (summary.pendingCount() > 0) reasons.add("PENDING_KNOWLEDGE_INDEX");
        if (summary.failedCount() > 0) reasons.add("FAILED_KNOWLEDGE_INDEX");
        return check(
                "KNOWLEDGE_IMPORT",
                summary.pendingCount(),
                summary.failedCount(),
                summary.oldestFailureAt(),
                reasons);
    }

    private static Check dlqCheck(DlqSummary summary) {
        List<String> reasons = new ArrayList<>();
        if (summary.pendingCount() > 0) reasons.add("PENDING_DLQ_RECONCILIATION");
        if (summary.needsAttentionCount() > 0) reasons.add("DLQ_NEEDS_ATTENTION");
        return check(
                "RUNTIME_DLQ",
                summary.pendingCount(),
                summary.needsAttentionCount(),
                summary.oldestPendingAt(),
                reasons);
    }

    private static Check check(
            String code,
            long pendingCount,
            long failedCount,
            Instant oldestAt,
            List<String> reasonCodes) {
        return new Check(
                code,
                reasonCodes.isEmpty() ? "passed" : "attention",
                pendingCount,
                failedCount,
                oldestAt,
                List.copyOf(reasonCodes));
    }

    private static int normalizeThreshold(int threshold) {
        if (threshold < 1 || threshold > MAX_STALE_THRESHOLD_MINUTES) {
            throw new IllegalArgumentException(
                    "foodmate.admin.audit-report-stale-minutes must be between 1 and "
                            + MAX_STALE_THRESHOLD_MINUTES);
        }
        return threshold;
    }
}
