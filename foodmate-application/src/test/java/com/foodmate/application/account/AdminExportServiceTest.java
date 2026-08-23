package com.foodmate.application.account;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodmate.application.account.port.out.AdminExportRepository;
import com.foodmate.application.account.service.AdminExportService;
import com.foodmate.application.account.service.AdminOperationalQueryService;
import com.foodmate.application.common.port.out.ObjectStoragePort;
import com.foodmate.application.common.service.OperationAuditService;
import com.foodmate.shared.account.enums.UserRole;
import com.foodmate.shared.error.BusinessException;
import com.foodmate.shared.id.IdGenerator;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;

class AdminExportServiceTest {
    private AdminExportRepository store;
    private AdminOperationalQueryService queries;
    private ObjectStoragePort storage;
    private IdGenerator ids;
    private OperationAuditService audit;
    private AdminExportService service;

    @BeforeEach
    void setUp() {
        store = Mockito.mock(AdminExportRepository.class);
        queries = Mockito.mock(AdminOperationalQueryService.class);
        storage = Mockito.mock(ObjectStoragePort.class);
        ids = Mockito.mock(IdGenerator.class);
        audit = Mockito.mock(OperationAuditService.class);
        when(ids.nextId()).thenReturn(42L);
        when(audit.findIdempotency(anyLong(), anyString())).thenReturn(null);
        when(audit.reserve(
                        anyLong(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        any()))
                .thenReturn(1);
        when(store.insertJob(anyLong(), anyLong(), anyString(), anyString(), anyString()))
                .thenReturn(1);
        service =
                new AdminExportService(
                        provider(store),
                        queries,
                        provider(storage),
                        ids,
                        new ObjectMapper(),
                        audit,
                        "foodmate-private");
    }

    @Test
    void adminCanCreateRedactedOperationAuditExport() {
        var result =
                service.request(
                        7L,
                        UserRole.ADMIN,
                        new AdminExportService.Request(
                                "operation-audits",
                                "knowledge",
                                "success",
                                null,
                                null,
                                "desc",
                                List.of("action", "request_id")),
                        "export-1");

        assertEquals(42L, result.exportJobId());
        verify(store)
                .insertJob(
                        anyLong(),
                        Mockito.eq(7L),
                        Mockito.eq("operation-audits"),
                        anyString(),
                        anyString());
        verify(audit).complete(Mockito.eq(7L), Mockito.eq("export-1"), anyString());
    }

    @Test
    void operatorCannotCreateExport() {
        assertThrows(
                BusinessException.class,
                () ->
                        service.request(
                                7L,
                                UserRole.OPERATOR,
                                new AdminExportService.Request(
                                        "operation-audits", null, null, null, null, null, null),
                                "export-operator"));
    }

    @Test
    void adminCannotExportUserOrDeletedResource() {
        assertThrows(
                BusinessException.class,
                () ->
                        service.request(
                                7L,
                                UserRole.ADMIN,
                                new AdminExportService.Request(
                                        "users", null, null, null, null, null, null),
                                "export-users"));
    }

    @Test
    void unknownFieldIsRejectedBeforeJobCreation() {
        assertThrows(
                BusinessException.class,
                () ->
                        service.request(
                                7L,
                                UserRole.SUPERADMIN,
                                new AdminExportService.Request(
                                        "operation-audits",
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        List.of("request_json")),
                                "export-sensitive"));
        Mockito.verifyNoInteractions(store);
    }

    @Test
    void workerWritesOnlySelectedSafeFields() throws Exception {
        when(store.queuedJobs(2)).thenReturn(List.of(42L));
        when(store.find(42L))
                .thenReturn(
                        new AdminExportRepository.JobRow(
                                42L,
                                7L,
                                "operation-audits",
                                "{\"query\":null,\"status\":null,\"visibility\":null,\"sort\":null,\"direction\":\"desc\"}",
                                "[\"action\"]",
                                "queued",
                                null,
                                null,
                                null,
                                null,
                                null));
        when(store.startJob(42L)).thenReturn(1);
        when(store.completeJob(42L, "admin-exports/7/42.json")).thenReturn(1);
        doReturn(
                        new AdminOperationalQueryService.Page<
                                AdminOperationalQueryService.OperationAudit>(
                                List.of(
                                        new AdminOperationalQueryService.OperationAudit(
                                                7L,
                                                "admin.export.request",
                                                "admin_export_request",
                                                "42",
                                                "success",
                                                "request-1",
                                                "trace-1",
                                                Instant.parse("2026-08-22T00:00:00Z"))),
                                1,
                                1,
                                100))
                .when(queries)
                .query(anyString(), any());

        service.processJobs();

        ArgumentCaptor<java.io.InputStream> input =
                ArgumentCaptor.forClass(java.io.InputStream.class);
        verify(storage)
                .put(
                        Mockito.eq("foodmate-private"),
                        Mockito.eq("admin-exports/7/42.json"),
                        input.capture(),
                        anyLong(),
                        Mockito.eq("application/json"));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        input.getValue().transferTo(bytes);
        String output = bytes.toString(java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(output.contains("\"action\""));
        assertFalse(output.contains("request_id"));
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> provider(T value) {
        ObjectProvider<T> provider = Mockito.mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(value);
        return provider;
    }
}
