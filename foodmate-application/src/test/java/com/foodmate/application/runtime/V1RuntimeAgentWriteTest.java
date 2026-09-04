package com.foodmate.application.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.foodmate.application.runtime.port.out.RuntimeEventRepository;
import com.foodmate.application.runtime.service.impl.V1RuntimeEventServiceImpl;
import com.foodmate.shared.runtime.V1RunEvent;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

class V1RuntimeAgentWriteTest {
    @Test
    void approvalCompletionIsIdempotentAndCreatesOnlyOneTerminalEventInMemory() {
        V1RuntimeEventServiceImpl service = new V1RuntimeEventServiceImpl(nullProvider(), () -> 1L);

        var first = service.completeAgentWrite(42L, 7L, 99L, "request-1", "trace-1", true);
        var replay = service.completeAgentWrite(42L, 7L, 99L, "request-1", "trace-1", true);

        assertEquals(false, first.duplicate());
        assertEquals(true, replay.duplicate());
        assertEquals("completed", service.status("42"));
        assertEquals(1, service.events("42").size());
        assertEquals("run.completed", service.events("42").getFirst().eventType());
        assertEquals("99", service.events("42").getFirst().payload().path("food_log_id").asText());
    }

    @Test
    void rejectedApprovalCompletesSafelyWithoutAResourceId() {
        V1RuntimeEventServiceImpl service = new V1RuntimeEventServiceImpl(nullProvider(), () -> 1L);

        service.completeAgentWrite(42L, 8L, null, "request-2", "trace-2", false);

        var event = service.events("42").getFirst();
        assertEquals("run.completed", event.eventType());
        assertEquals(true, event.payload().path("write_skipped").asBoolean());
        assertEquals(false, event.payload().has("food_log_id"));
    }

    @Test
    void persistedApprovalCompletionIsDeduplicatedByTheEventFact() {
        RuntimeEventRepository store = mock(RuntimeEventRepository.class);
        AtomicReference<String> knownHash = new AtomicReference<>();
        when(store.runExists(42L)).thenReturn(true);
        when(store.status(42L)).thenReturn("waiting_user");
        when(store.activeDispatch(42L))
                .thenReturn(new RuntimeEventRepository.ActiveDispatch("dispatch-1", 1, 5));
        when(store.dispatch(42L, "dispatch-1"))
                .thenReturn(new RuntimeEventRepository.DispatchRow(1L, 5L, "active", 1));
        when(store.eventHash(42L, "approval-completed-9"))
                .thenAnswer(invocation -> knownHash.get());
        when(store.lockNextSseSequence(42L)).thenReturn(1L);
        when(store.assistantExists(42L)).thenReturn(true);
        var eventHash = new AtomicReference<String>();
        org.mockito.Mockito.doAnswer(
                        invocation -> {
                            V1RunEvent event = invocation.getArgument(2);
                            eventHash.set(event.requestHash());
                            knownHash.set(event.requestHash());
                            return null;
                        })
                .when(store)
                .insertEvent(anyLong(), anyLong(), any(), anyString());
        V1RuntimeEventServiceImpl service =
                new V1RuntimeEventServiceImpl(provider(store), () -> 1L);

        var first = service.completeAgentWrite(42L, 9L, 100L, "request-3", "trace-3", true);
        var replay = service.completeAgentWrite(42L, 9L, 100L, "request-3", "trace-3", true);

        assertEquals(false, first.duplicate());
        assertEquals(true, replay.duplicate());
        assertEquals(first.eventId(), replay.eventId());
        assertEquals("sha256:", eventHash.get().substring(0, 7));
        verify(store, org.mockito.Mockito.times(1))
                .insertEvent(anyLong(), anyLong(), any(), anyString());
        verify(store).updateRun(eq(42L), eq("completed"), anyString());
    }

    private static <T> ObjectProvider<T> provider(T value) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(value);
        return provider;
    }

    private static ObjectProvider<RuntimeEventRepository> nullProvider() {
        return new ObjectProvider<>() {
            @Override
            public RuntimeEventRepository getObject(Object... args) {
                return null;
            }

            @Override
            public RuntimeEventRepository getIfAvailable() {
                return null;
            }

            @Override
            public RuntimeEventRepository getIfUnique() {
                return null;
            }

            @Override
            public Stream<RuntimeEventRepository> orderedStream() {
                return Stream.empty();
            }

            @Override
            public Stream<RuntimeEventRepository> stream() {
                return Stream.empty();
            }
        };
    }
}
