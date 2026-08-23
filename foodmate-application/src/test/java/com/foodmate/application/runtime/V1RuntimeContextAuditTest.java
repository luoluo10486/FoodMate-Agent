package com.foodmate.application.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.foodmate.application.common.service.OperationAuditService;
import com.foodmate.application.runtime.port.out.RuntimeEventRepository;
import com.foodmate.application.runtime.service.impl.V1RuntimeEventServiceImpl;
import com.foodmate.shared.runtime.V1RunEvent;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class V1RuntimeContextAuditTest {
    @Test
    void contextAssemblyPersistsOnlySourceIdentifiers() {
        RuntimeEventRepository store = store();
        OperationAuditService audit = mock(OperationAuditService.class);
        V1RuntimeEventServiceImpl service = service(store, audit);

        var result = service.accept(event(payload()));

        assertEquals("unchanged", result.status());
        verify(audit)
                .record(
                        eq(42L),
                        eq("agent_run"),
                        eq("7"),
                        eq("agent_run.context.assembled"),
                        eq("success"),
                        eq(null),
                        eq(null),
                        eq(null),
                        eq(
                                Map.of(
                                        "context_source_message_ids", "message-1,message-2",
                                        "context_source_summary_ids", "summary-1",
                                        "context_source_memory_ids", "memory-1",
                                        "context_source_citation_ids", "citation-1")));
        verify(store).insertEvent(anyLong(), eq(7L), any(), any());
    }

    @Test
    void auditFailurePreventsContextEventPersistence() {
        RuntimeEventRepository store = store();
        OperationAuditService audit = mock(OperationAuditService.class);
        doThrow(new IllegalStateException("audit unavailable"))
                .when(audit)
                .record(any(), any(), any(), any(), any(), any(), any(), any(), any());
        V1RuntimeEventServiceImpl service = service(store, audit);

        assertThrows(IllegalStateException.class, () -> service.accept(event(payload())));

        verify(store, never()).insertEvent(anyLong(), anyLong(), any(), any());
    }

    private static V1RuntimeEventServiceImpl service(
            RuntimeEventRepository store, OperationAuditService audit) {
        return new V1RuntimeEventServiceImpl(
                provider(store),
                () -> 100L,
                emptyProvider(),
                emptyProvider(),
                emptyProvider(),
                provider(audit));
    }

    private static RuntimeEventRepository store() {
        RuntimeEventRepository store = mock(RuntimeEventRepository.class);
        when(store.runExists(7L)).thenReturn(true);
        when(store.eventHash(7L, "event-1")).thenReturn(null);
        when(store.dispatch(7L, "dispatch-1"))
                .thenReturn(new RuntimeEventRepository.DispatchRow(1L, 2L, "active", 1));
        when(store.lockOwner(7L)).thenReturn(new RuntimeEventRepository.RunOwner(99L, 42L));
        when(store.lockNextSseSequence(7L)).thenReturn(1L);
        return store;
    }

    private static V1RunEvent event(ObjectNode payload) {
        return new V1RunEvent(
                "v1",
                "7",
                "dispatch-1",
                1,
                "event-1",
                3,
                "request-1",
                "trace-1",
                "hash-1",
                Instant.parse("2026-08-23T00:00:00Z"),
                "run.context_assembled",
                payload);
    }

    private static ObjectNode payload() {
        ObjectNode payload = JsonNodeFactory.instance.objectNode();
        ObjectNode sourceIds = payload.putObject("source_ids");
        array(sourceIds, "message_id", "message-1", "message-2");
        array(sourceIds, "summary_id", "summary-1");
        array(sourceIds, "memory_id", "memory-1");
        array(sourceIds, "citation_id", "citation-1");
        payload.put("private_text", "must not be audited");
        return payload;
    }

    private static void array(ObjectNode parent, String name, String... values) {
        ArrayNode target = parent.putArray(name);
        for (String value : values) target.add(value);
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> provider(T value) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(value);
        return provider;
    }

    private static <T> ObjectProvider<T> emptyProvider() {
        return provider(null);
    }
}
