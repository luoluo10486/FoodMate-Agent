package com.foodmate.application.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.foodmate.application.account.port.out.AdminAuditReportRepository;
import com.foodmate.application.account.port.out.AdminAuditReportRepository.DlqSummary;
import com.foodmate.application.account.port.out.AdminAuditReportRepository.KnowledgeImportSummary;
import com.foodmate.application.account.port.out.AdminAuditReportRepository.OperationAuditSummary;
import com.foodmate.application.account.port.out.AdminAuditReportRepository.OutboxSummary;
import com.foodmate.application.account.service.AdminAuditReportService;
import com.foodmate.application.account.service.impl.AdminAuditReportServiceImpl;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class AdminAuditReportServiceImplTest {
    private static final Instant STALE_AT = Instant.parse("2026-08-23T01:00:00Z");

    private AdminAuditReportRepository store;

    @BeforeEach
    void setUp() {
        store = Mockito.mock(AdminAuditReportRepository.class);
        when(store.operationAudits()).thenReturn(new OperationAuditSummary(0, 0, null));
        when(store.runtimeDispatchOutbox(anyInstant())).thenReturn(new OutboxSummary(0, 0, null));
        when(store.knowledgeIndexOutbox(anyInstant())).thenReturn(new OutboxSummary(0, 0, null));
        when(store.knowledgeVisibilityOutbox(anyInstant()))
                .thenReturn(new OutboxSummary(0, 0, null));
        when(store.agentRunSseOutbox(anyInstant())).thenReturn(new OutboxSummary(0, 0, null));
        when(store.knowledgeImports()).thenReturn(new KnowledgeImportSummary(0, 0, null));
        when(store.dlq()).thenReturn(new DlqSummary(0, 0, null));
    }

    @Test
    void returnsPassedWhenAllOperationalFactsAreClean() {
        AdminAuditReportService.Report report = new AdminAuditReportServiceImpl(store).current();

        assertEquals("passed", report.status());
        assertEquals(7, report.checks().size());
        assertEquals(
                List.of(),
                report.checks().stream().flatMap(check -> check.reasonCodes().stream()).toList());
    }

    @Test
    void returnsAttentionWithStableReasonCodesAndNoPayload() {
        when(store.operationAudits()).thenReturn(new OperationAuditSummary(2, 1, STALE_AT));
        when(store.runtimeDispatchOutbox(anyInstant()))
                .thenReturn(new OutboxSummary(3, 0, STALE_AT));
        when(store.knowledgeImports()).thenReturn(new KnowledgeImportSummary(0, 4, STALE_AT));
        when(store.dlq()).thenReturn(new DlqSummary(1, 2, STALE_AT));

        AdminAuditReportService.Report report = new AdminAuditReportServiceImpl(store).current();

        assertEquals("attention", report.status());
        AdminAuditReportService.Check audit = report.checks().getFirst();
        assertEquals(
                List.of("PENDING_OPERATION_AUDIT", "FAILED_OPERATION_AUDIT"), audit.reasonCodes());
        assertEquals(
                List.of("PENDING_DLQ_RECONCILIATION", "DLQ_NEEDS_ATTENTION"),
                report.checks().getLast().reasonCodes());
    }

    @Test
    void rejectsUnsafeStaleThreshold() {
        assertThrows(
                IllegalArgumentException.class, () -> new AdminAuditReportServiceImpl(store, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new AdminAuditReportServiceImpl(store, 1_441));
    }

    private static Instant anyInstant() {
        return Mockito.any(Instant.class);
    }
}
