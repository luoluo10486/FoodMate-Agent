package com.foodmate.application.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import com.foodmate.shared.runtime.RunCommand;
import com.foodmate.shared.runtime.RunEvent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RuntimeGatewayServiceTest {
    @Test void dispatchIsIdempotentAndConflictsOnChangedRequest() {
        var service = new RuntimeGatewayService();
        var command = new RunCommand("d1", "r1", "hello", Instant.now().plusSeconds(30), 1);
        assertEquals(false, service.dispatch(command).duplicate());
        assertEquals(true, service.dispatch(command).duplicate());
        var changed = new RunCommand("d1", "r1", "changed", command.deadlineAt(), 1);
        assertEquals("RUNTIME_DISPATCH_IDEMPOTENCY_CONFLICT", assertThrows(RuntimeGatewayService.IdempotencyConflict.class, () -> service.dispatch(changed)).getMessage());
    }

    @Test void configuredDataStoreWithoutRuntimeFailsBeforeWriting() {
        var service = new RuntimeGatewayService(
                new org.springframework.beans.factory.ObjectProvider<org.springframework.jdbc.core.JdbcTemplate>() {
                    public org.springframework.jdbc.core.JdbcTemplate getObject(Object... args) { return null; }
                    public org.springframework.jdbc.core.JdbcTemplate getIfAvailable() { return new org.springframework.jdbc.core.JdbcTemplate(); }
                    public org.springframework.jdbc.core.JdbcTemplate getIfUnique() { return null; }
                    public java.util.stream.Stream<org.springframework.jdbc.core.JdbcTemplate> orderedStream() { return java.util.stream.Stream.empty(); }
                    public java.util.stream.Stream<org.springframework.jdbc.core.JdbcTemplate> stream() { return java.util.stream.Stream.empty(); }
                },
                new org.springframework.beans.factory.ObjectProvider<com.foodmate.gateway.GatewayClient>() {
                    public com.foodmate.gateway.GatewayClient getObject(Object... args) { return null; }
                    public com.foodmate.gateway.GatewayClient getIfAvailable() { return null; }
                    public com.foodmate.gateway.GatewayClient getIfUnique() { return null; }
                    public java.util.stream.Stream<com.foodmate.gateway.GatewayClient> orderedStream() { return java.util.stream.Stream.empty(); }
                    public java.util.stream.Stream<com.foodmate.gateway.GatewayClient> stream() { return java.util.stream.Stream.empty(); }
                },
                new org.springframework.beans.factory.ObjectProvider<com.foodmate.application.account.UserAccountService>() {
                    public com.foodmate.application.account.UserAccountService getObject(Object... args) { return null; }
                    public com.foodmate.application.account.UserAccountService getIfAvailable() { return null; }
                    public com.foodmate.application.account.UserAccountService getIfUnique() { return null; }
                    public java.util.stream.Stream<com.foodmate.application.account.UserAccountService> orderedStream() { return java.util.stream.Stream.empty(); }
                    public java.util.stream.Stream<com.foodmate.application.account.UserAccountService> stream() { return java.util.stream.Stream.empty(); }
                });
        assertEquals("RUNTIME_UNAVAILABLE", assertThrows(com.foodmate.shared.runtime.RuntimeException.class,
                () -> service.dispatch(new RunCommand("d-missing", "r-missing", "hello", Instant.now().plusSeconds(30), 1))).code());
    }

    @Test void eventsDriveRunStateAndRejectIllegalOrder() {
        var service = new RuntimeGatewayService();
        var deadline = Instant.now().plusSeconds(30);
        service.dispatch(new RunCommand("d1", "r1", "hello", deadline, 1));
        assertEquals(RuntimeGatewayService.Status.DISPATCHED, service.event(new RunEvent("e1", "r1", 1, RunEvent.State.DISPATCHED, null, Instant.now())).status());
        assertEquals(RuntimeGatewayService.Status.RUNNING, service.event(new RunEvent("e2", "r1", 2, RunEvent.State.RUNNING, null, Instant.now())).status());
        assertEquals(RuntimeGatewayService.Status.SUCCEEDED, service.event(new RunEvent("e3", "r1", 3, RunEvent.State.SUCCEEDED, "ok", Instant.now())).status());
        assertEquals("RUNTIME_STATE_CONFLICT", assertThrows(com.foodmate.shared.runtime.RuntimeException.class,
                () -> service.event(new RunEvent("e4", "r1", 4, RunEvent.State.RUNNING, null, Instant.now()))).code());
    }

    @Test void cancelAndDispatchDeadlinesAreEnforced() {
        var service = new RuntimeGatewayService();
        var expired = Instant.now().minusSeconds(1);
        assertEquals("RUNTIME_DEADLINE_EXCEEDED", assertThrows(com.foodmate.shared.runtime.RuntimeException.class,
                () -> service.dispatch(new RunCommand("d1", "r1", "hello", expired, 1))).code());
        service.dispatch(new RunCommand("d2", "r2", "hello", Instant.now().plusSeconds(30), 1));
        assertEquals("RUNTIME_DEADLINE_EXCEEDED", assertThrows(com.foodmate.shared.runtime.RuntimeException.class,
                () -> service.cancel(new com.foodmate.shared.runtime.CancelCommand("c1", "r2", "timeout", expired))).code());
    }
    @Test void subscribersReceiveOnlyAcceptedEventsAndCanResumeFromSequence() {
        var service = new RuntimeGatewayService();
        service.dispatch(new RunCommand("d-stream", "r-stream", "hello", Instant.now().plusSeconds(30), 1));
        List<Long> live = new ArrayList<>();
        service.subscribe("r-stream", 0, event -> live.add(event.eventSeq()));
        service.event(new RunEvent("e1", "r-stream", 1, RunEvent.State.DISPATCHED, null, Instant.now()));
        service.event(new RunEvent("e2", "r-stream", 2, RunEvent.State.RUNNING, null, Instant.now()));
        service.event(new RunEvent("e2", "r-stream", 2, RunEvent.State.RUNNING, null, Instant.now()));
        assertEquals(List.of(1L, 2L), live);

        List<Long> resumed = new ArrayList<>();
        service.subscribe("r-stream", 1, event -> resumed.add(event.eventSeq()));
        assertEquals(List.of(2L), resumed);
    }
}
