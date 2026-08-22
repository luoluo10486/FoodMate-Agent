package com.foodmate.application.retention;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.foodmate.application.common.service.OperationAuditService;
import com.foodmate.application.retention.port.out.DataRetentionRepository;
import com.foodmate.application.retention.service.DataRetentionService;
import com.foodmate.application.retention.service.impl.DataRetentionServiceImpl;
import com.foodmate.shared.account.enums.UserRole;
import com.foodmate.shared.error.BusinessException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class DataRetentionServiceImplTest {
    private static final Instant NOW = Instant.parse("2026-08-23T00:00:00Z");
    private DataRetentionRepository store;
    private OperationAuditService audit;
    private DataRetentionServiceImpl service;

    @BeforeEach
    void setUp() {
        store = Mockito.mock(DataRetentionRepository.class);
        audit = Mockito.mock(OperationAuditService.class);
        service =
                new DataRetentionServiceImpl(
                        store,
                        audit,
                        () -> 9001L,
                        Clock.fixed(NOW, ZoneOffset.UTC),
                        "foodmate-private");
        when(audit.findIdempotency(anyLong(), anyString())).thenReturn(null);
        when(audit.reserve(
                        anyLong(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        any(Map.class)))
                .thenReturn(1);
        when(store.policy("knowledge_document"))
                .thenReturn(
                        new DataRetentionRepository.Policy(
                                1L, "knowledge_document", 30, true, "p-v1"));
        when(store.resource("knowledge_document", 42L))
                .thenReturn(
                        new DataRetentionRepository.ResourceSnapshot(
                                "knowledge_document",
                                42L,
                                true,
                                NOW.minusSeconds(31L * 86400L),
                                2L,
                                "knowledge/public/42/guide.md",
                                "v1"));
        when(store.insertPurgeRequest(any())).thenReturn(1);
        when(store.insertPurgeTask(any())).thenReturn(1);
        when(store.approvePurge(anyLong(), anyLong(), any())).thenReturn(1);
    }

    @Test
    void adminCanRequestButCannotApproveAndPayloadNeverEntersAudit() {
        DataRetentionService.PurgeResult result = service.requestPurge(purgeCommand("purge-42"));

        assertEquals(9001L, result.requestId());
        assertEquals("requested", result.status());
        verify(audit)
                .complete(
                        7L,
                        "purge-42",
                        "{\"requestId\":9001,\"status\":\"requested\",\"resourceType\":\"knowledge_document\",\"resourceId\":42,\"eligibleAt\":\"2026-08-22T00:00:00Z\",\"taskCount\":0}");
    }

    @Test
    void activeHoldBlocksPurgeBeforeAuditReservation() {
        when(store.activeHold("knowledge_document", 42L))
                .thenReturn(
                        new DataRetentionRepository.Hold(
                                88L, "knowledge_document", 42L, "legal_case", 8L, "active", NOW));

        BusinessException exception =
                assertThrows(
                        BusinessException.class,
                        () -> service.requestPurge(purgeCommand("purge-42")));

        assertEquals("RETENTION_HOLD_ACTIVE", exception.errorCode().code());
        Mockito.verify(audit, Mockito.never())
                .reserve(
                        anyLong(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        any(Map.class));
    }

    @Test
    void adminCanPlaceHoldBeforeResourceIsSoftDeleted() {
        when(store.resource("knowledge_document", 42L))
                .thenReturn(
                        new DataRetentionRepository.ResourceSnapshot(
                                "knowledge_document",
                                42L,
                                false,
                                null,
                                2L,
                                "knowledge/public/42/guide.md",
                                "v1"));
        when(store.insertHold(any())).thenReturn(1);

        DataRetentionService.HoldResult result =
                service.placeHold(
                        new DataRetentionService.HoldCommand(
                                7L,
                                UserRole.ADMIN,
                                "trace",
                                "hold-42",
                                "knowledge_document",
                                42L,
                                "legal_case",
                                true,
                                DataRetentionService.holdConfirmationDigest(
                                        "knowledge_document", 42L, "legal_case")));

        assertEquals("active", result.status());
        verify(store).insertHold(any());
    }

    @Test
    void onlySuperadminCanApproveAndApprovalCreatesThreeIdempotentTasks() {
        when(store.purgeRequest(901L))
                .thenReturn(
                        new DataRetentionRepository.PurgeRequest(
                                901L,
                                "knowledge_document",
                                42L,
                                1L,
                                7L,
                                "requested",
                                NOW.minusSeconds(1),
                                null,
                                null,
                                0));

        BusinessException forbidden =
                assertThrows(
                        BusinessException.class,
                        () ->
                                service.approvePurge(
                                        901L,
                                        new DataRetentionService.ApprovalCommand(
                                                7L,
                                                UserRole.ADMIN,
                                                "trace",
                                                "approve-901",
                                                true,
                                                DataRetentionService.approvalConfirmationDigest(
                                                        901L))));
        assertEquals("RETENTION_APPROVAL_REQUIRED", forbidden.errorCode().code());

        DataRetentionService.PurgeResult result =
                service.approvePurge(
                        901L,
                        new DataRetentionService.ApprovalCommand(
                                8L,
                                UserRole.SUPERADMIN,
                                "trace",
                                "approve-901",
                                true,
                                DataRetentionService.approvalConfirmationDigest(901L)));

        assertEquals("approved", result.status());
        assertEquals(3, result.taskCount());
        verify(store, Mockito.times(3)).insertPurgeTask(any());
    }

    private DataRetentionService.PurgeCommand purgeCommand(String key) {
        return new DataRetentionService.PurgeCommand(
                7L,
                UserRole.ADMIN,
                "trace",
                key,
                "knowledge_document",
                42L,
                true,
                DataRetentionService.purgeConfirmationDigest("knowledge_document", 42L));
    }
}
