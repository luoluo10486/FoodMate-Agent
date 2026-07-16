package com.foodmate.application.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import com.foodmate.shared.runtime.RunCommand;
import com.foodmate.shared.runtime.RunEvent;
import java.time.Instant;
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
}
