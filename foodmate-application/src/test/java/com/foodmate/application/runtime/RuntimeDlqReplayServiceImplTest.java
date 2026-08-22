package com.foodmate.application.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.foodmate.application.common.port.out.OperationAuditPort.IdempotencyRecord;
import com.foodmate.application.common.service.OperationAuditService;
import com.foodmate.application.runtime.port.out.DeadLetterRepository;
import com.foodmate.application.runtime.port.out.DeadLetterRepository.ReplayCandidate;
import com.foodmate.application.runtime.service.RuntimeDlqReplayService;
import com.foodmate.application.runtime.service.impl.RuntimeDlqReplayServiceImpl;
import com.foodmate.shared.account.enums.UserRole;
import com.foodmate.shared.error.BusinessException;
import com.foodmate.shared.id.IdGenerator;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class RuntimeDlqReplayServiceImplTest {
    private DeadLetterRepository store;
    private OperationAuditService audit;
    private RuntimeDlqReplayServiceImpl service;
    private ReplayCandidate candidate;

    @BeforeEach
    void setUp() {
        store = Mockito.mock(DeadLetterRepository.class);
        audit = Mockito.mock(OperationAuditService.class);
        IdGenerator ids = () -> 9001L;
        service = new RuntimeDlqReplayServiceImpl(store, audit, ids);
        candidate =
                new ReplayCandidate(
                        11L,
                        "foodmate-java-agent-event-v1",
                        "foodmate-agent-event-v1",
                        "broker-original-11",
                        "run-11",
                        "11",
                        "dispatch-11",
                        1,
                        "event-11",
                        4L,
                        "sha256:request-11",
                        "{\"event_type\":\"run.completed\"}");
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
        when(store.findReplayCandidate(11L)).thenReturn(candidate);
        when(store.insertReplay(any())).thenReturn(1);
    }

    @Test
    void superadminCreatesAuditedReplayOutboxWithoutPayloadInAuditMetadata() {
        RuntimeDlqReplayService.ReplayResult result = service.request(11L, command("replay-11"));

        assertEquals(9001L, result.replayId());
        assertEquals("queued", result.status());
        assertEquals("broker-original-11", result.originalMessageId());
        verify(audit)
                .complete(
                        7L,
                        "replay-11",
                        "{\"replayId\":9001,\"dlqId\":11,\"status\":\"queued\",\"originalMessageId\":\"broker-original-11\"}");
    }

    @Test
    void ordinaryAdminCannotReplay() {
        RuntimeDlqReplayService.Command command =
                new RuntimeDlqReplayService.Command(
                        7L,
                        UserRole.ADMIN,
                        "replay-11",
                        true,
                        RuntimeDlqReplayService.confirmationDigest(11L));

        BusinessException exception =
                assertThrows(BusinessException.class, () -> service.request(11L, command));

        assertEquals("FORBIDDEN", exception.errorCode().code());
        Mockito.verifyNoInteractions(store);
    }

    @Test
    void missingOriginalPayloadFactsFailClosed() {
        when(store.findReplayCandidate(11L))
                .thenReturn(
                        new ReplayCandidate(
                                11L,
                                "foodmate-java-agent-event-v1",
                                "foodmate-agent-event-v1",
                                "broker-original-11",
                                "run-11",
                                "11",
                                "dispatch-11",
                                1,
                                "event-11",
                                4L,
                                "sha256:request-11",
                                null));

        BusinessException exception =
                assertThrows(
                        BusinessException.class, () -> service.request(11L, command("replay-11")));

        assertEquals("DLQ_REPLAY_FACT_INCOMPLETE", exception.errorCode().code());
        Mockito.verify(audit, Mockito.never()).complete(anyLong(), anyString(), anyString());
        Mockito.verify(store, Mockito.never()).insertReplay(any());
    }

    @Test
    void sameIdempotencyKeyReplaysCompletedFact() {
        when(audit.findIdempotency(7L, "replay-11"))
                .thenReturn(
                        new IdempotencyRecord(
                                digest(),
                                "success",
                                "{\"replayId\":9001,\"dlqId\":11,\"status\":\"queued\",\"originalMessageId\":\"broker-original-11\"}"));

        RuntimeDlqReplayService.ReplayResult result = service.request(11L, command("replay-11"));

        assertEquals(9001L, result.replayId());
        Mockito.verify(store, Mockito.never()).insertReplay(any());
    }

    private RuntimeDlqReplayService.Command command(String key) {
        return new RuntimeDlqReplayService.Command(
                7L,
                UserRole.SUPERADMIN,
                key,
                true,
                RuntimeDlqReplayService.confirmationDigest(11L));
    }

    private String digest() {
        try {
            return "sha256:"
                    + HexFormat.of()
                            .formatHex(
                                    MessageDigest.getInstance("SHA-256")
                                            .digest(
                                                    "runtime.dlq.replay|11"
                                                            .getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
    }
}
