package com.foodmate.application.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.foodmate.application.runtime.persistence.RuntimeRecoveryStore;
import com.foodmate.application.runtime.persistence.RuntimeRecoveryStore.CheckpointFact;
import com.foodmate.application.runtime.persistence.RuntimeRecoveryStore.RecoveryRequest;
import com.foodmate.application.runtime.persistence.RuntimeRecoveryStore.RecoveryRun;
import com.foodmate.shared.id.IdGenerator;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;

class RuntimeRecoveryServiceTest {
    @Test
    void recoveryCreatesNewAttemptWithReconciliationContext() throws Exception {
        RuntimeRecoveryStore store = Mockito.mock(RuntimeRecoveryStore.class);
        AgentAdmissionService admission = Mockito.mock(AgentAdmissionService.class);
        IdGenerator ids = Mockito.mock(IdGenerator.class);
        ObjectProvider<RuntimeRecoveryStore> provider = Mockito.mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(store);
        when(ids.nextId()).thenReturn(100L, 101L);
        when(admission.admit("1", 7L, 9L, 10))
                .thenReturn(
                        new AgentAdmissionService.Admission(
                                AgentAdmissionService.State.ACTIVE, List.of()));
        Instant deadline = Instant.now().plusSeconds(60);
        when(store.lockRun(1L, 7L))
                .thenReturn(
                        new RecoveryRun(
                                "executing",
                                9L,
                                12L,
                                "d1",
                                1,
                                3L,
                                deadline,
                                2,
                                """
                                {"run_id":"1","dispatch_id":"d1","attempt":1,
                                 "request_id":"req-old","deadline_at":"%s",
                                 "runtime_options":{"budget_snapshot":{"revision":2}}}
                                """
                                        .formatted(deadline)));
        when(store.completedInvocationIds(1L)).thenReturn(List.of("inv-1"));
        when(store.completedToolResults(1L))
                .thenReturn(
                        List.of(
                                "{\"proposal_id\":\"p1\",\"invocation_id\":\"inv-1\","
                                        + "\"request_hash\":\"sha256:p1\",\"status\":\"succeeded\","
                                        + "\"error_code\":\"\",\"rows\":[]}"));
        when(store.latestCheckpoint(1L, "d1"))
                .thenReturn(
                        new CheckpointFact(4, "sha256:checkpoint", 2, "tool_wait", "[\"inv-1\"]"));

        RuntimeRecoveryService service = new RuntimeRecoveryService(provider, ids, admission, 10);
        RuntimeRecoveryService.RecoveryResult result =
                service.recover(
                        new RecoveryRequest(7L, 1L, 4, "sha256:checkpoint", List.of("inv-1")));

        assertEquals("1", result.runId());
        assertEquals(2, result.attempt());
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(store)
                .insertOutbox(
                        anyLong(),
                        anyLong(),
                        anyLong(),
                        any(),
                        anyInt(),
                        any(),
                        anyLong(),
                        payload.capture(),
                        any());
        String body = payload.getValue();
        assertEquals(
                "d1",
                new com.fasterxml.jackson.databind.ObjectMapper()
                        .readTree(body)
                        .path("recovery_context")
                        .path("previous_dispatch_id")
                        .asText());
        assertEquals(
                1,
                new com.fasterxml.jackson.databind.ObjectMapper()
                        .readTree(body)
                        .path("recovery_context")
                        .path("previous_attempt")
                        .asInt());
        assertEquals(
                "inv-1",
                new com.fasterxml.jackson.databind.ObjectMapper()
                        .readTree(body)
                        .path("recovery_context")
                        .path("completed_tool_results")
                        .get(0)
                        .path("invocation_id")
                        .asText());
        verify(store).expireDispatch(12L);
        verify(store).expireOutbox(12L);
    }

    @Test
    void persistedCheckpointTriggerBuildsRecoveryRequestFromJavaFact() {
        RuntimeRecoveryStore store = Mockito.mock(RuntimeRecoveryStore.class);
        AgentAdmissionService admission = Mockito.mock(AgentAdmissionService.class);
        IdGenerator ids = Mockito.mock(IdGenerator.class);
        ObjectProvider<RuntimeRecoveryStore> provider = Mockito.mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(store);
        when(ids.nextId()).thenReturn(100L, 101L);
        when(admission.admit("1", 7L, 9L, 10))
                .thenReturn(
                        new AgentAdmissionService.Admission(
                                AgentAdmissionService.State.ACTIVE, List.of()));
        Instant deadline = Instant.now().plusSeconds(60);
        RecoveryRun run =
                new RecoveryRun(
                        "executing",
                        9L,
                        12L,
                        "d1",
                        1,
                        3L,
                        deadline,
                        2,
                        "{\"run_id\":\"1\",\"dispatch_id\":\"d1\",\"attempt\":1,\"deadline_at\":\""
                                + deadline
                                + "\"}");
        when(store.lockRun(1L, 7L)).thenReturn(run);
        when(store.latestCheckpoint(1L, "d1"))
                .thenReturn(new CheckpointFact(4, "sha256:checkpoint", 2, "execution", "[]"));
        when(store.completedInvocationIds(1L)).thenReturn(List.of());

        RuntimeRecoveryService service = new RuntimeRecoveryService(provider, ids, admission, 10);
        RuntimeRecoveryService.RecoveryResult result =
                service.recoverFromPersistedCheckpoint(7L, 1L);

        assertEquals(2, result.attempt());
        verify(store, org.mockito.Mockito.atLeastOnce()).latestCheckpoint(1L, "d1");
    }

    @Test
    void terminalRunCannotBeRecovered() {
        RuntimeRecoveryStore store = Mockito.mock(RuntimeRecoveryStore.class);
        ObjectProvider<RuntimeRecoveryStore> provider = Mockito.mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(store);
        when(store.lockRun(1L, 7L))
                .thenReturn(
                        new RecoveryRun(
                                "completed",
                                9L,
                                12L,
                                "d1",
                                1,
                                3L,
                                Instant.now().plusSeconds(60),
                                2,
                                "{}"));

        RuntimeRecoveryService service =
                new RuntimeRecoveryService(
                        provider,
                        Mockito.mock(IdGenerator.class),
                        Mockito.mock(AgentAdmissionService.class),
                        10);

        assertEquals(
                "RECOVERY_RUN_TERMINAL",
                assertThrows(
                                com.foodmate.shared.runtime.RuntimeException.class,
                                () ->
                                        service.recover(
                                                new RecoveryRequest(
                                                        7L, 1L, 1, "sha256:x", List.of())))
                        .code());
    }
}
