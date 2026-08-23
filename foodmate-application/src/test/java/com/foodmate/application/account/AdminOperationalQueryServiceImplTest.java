package com.foodmate.application.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.foodmate.application.account.port.out.AdminOperationalQueryRepository;
import com.foodmate.application.account.service.AdminOperationalQueryService;
import com.foodmate.application.account.service.impl.AdminOperationalQueryServiceImpl;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class AdminOperationalQueryServiceImplTest {
    private AdminOperationalQueryRepository store;
    private AdminOperationalQueryService service;

    @BeforeEach
    void setUp() {
        store = Mockito.mock(AdminOperationalQueryRepository.class);
        service = new AdminOperationalQueryServiceImpl(store);
        when(store.runs(any())).thenReturn(List.of());
        when(store.countRuns(any())).thenReturn(0L);
    }

    @Test
    void clampsPageSizeAndUsesStableDefaultSort() {
        service.query(
                "runs",
                new AdminOperationalQueryService.Request(2, 500, null, null, null, null, null));

        ArgumentCaptor<AdminOperationalQueryRepository.Query> query =
                ArgumentCaptor.forClass(AdminOperationalQueryRepository.Query.class);
        verify(store).runs(query.capture());
        assertEquals("created_at", query.getValue().sort());
        assertEquals("desc", query.getValue().direction());
        assertEquals(100, query.getValue().limit());
        assertEquals(100, query.getValue().offset());
    }

    @Test
    void rejectsUnknownResourceAndSort() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        service.query(
                                "secrets",
                                new AdminOperationalQueryService.Request(
                                        1, 20, null, null, null, null, null)));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        service.query(
                                "runs",
                                new AdminOperationalQueryService.Request(
                                        1, 20, null, null, null, "sql_text", "desc")));
    }

    @Test
    void mapsOnlySafeSqlAuditSummary() {
        when(store.sqlAudits(any()))
                .thenReturn(
                        List.of(
                                new AdminOperationalQueryRepository.SqlAuditRow(
                                        8L, 3L, "hash", "executed", "trace", 12L, 2L, null, null)));
        when(store.countSqlAudits(any())).thenReturn(1L);

        var result =
                service.query(
                        "sql-audits",
                        new AdminOperationalQueryService.Request(
                                1, 20, null, null, null, null, null));

        var row = (AdminOperationalQueryService.SqlAudit) result.items().getFirst();
        assertEquals("hash", row.queryHash());
        assertEquals("executed", row.result());
    }

    @Test
    void exposesDlqIdentityAndReconciliationWithoutPayload() {
        when(store.dlq(any()))
                .thenReturn(
                        List.of(
                                new AdminOperationalQueryRepository.DlqRow(
                                        21L,
                                        "foodmate-java-agent-event-v1",
                                        "foodmate-agent-event-v1",
                                        "mq-21",
                                        "42",
                                        "dispatch-42",
                                        "event-42",
                                        2,
                                        8,
                                        "RUNTIME_MESSAGE_DEAD_LETTERED",
                                        "needs_attention",
                                        null,
                                        null)));
        when(store.countDlq(any())).thenReturn(1L);

        var result =
                service.query(
                        "dlq",
                        new AdminOperationalQueryService.Request(
                                1, 20, null, "needs_attention", null, "state", "asc"));

        var row = (AdminOperationalQueryService.Dlq) result.items().getFirst();
        assertEquals("mq-21", row.messageId());
        assertEquals("needs_attention", row.reconciliationState());
        assertEquals(8, row.reconsumeTimes());
        verify(store).dlq(any());
    }
}
