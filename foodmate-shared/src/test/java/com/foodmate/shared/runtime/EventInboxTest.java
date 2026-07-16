package com.foodmate.shared.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class EventInboxTest {
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private RunEvent event(String id, long seq, RunEvent.State state) { return new RunEvent(id, "run-1", seq, state, "body", NOW); }

    @Test void acceptsAndDeduplicatesSameEvent() {
        EventInbox inbox = new EventInbox();
        assertEquals(EventInbox.Result.ACCEPTED, inbox.accept(event("e1", 1, RunEvent.State.RUNNING)));
        assertEquals(EventInbox.Result.DUPLICATE, inbox.accept(event("e1", 1, RunEvent.State.RUNNING)));
    }
    @Test void rejectsConflictingDuplicate() {
        EventInbox inbox = new EventInbox(); inbox.accept(event("e1", 1, RunEvent.State.RUNNING));
        assertEquals("RUNTIME_EVENT_IDEMPOTENCY_CONFLICT", assertThrows(RuntimeException.class, () -> inbox.accept(event("e1", 1, RunEvent.State.FAILED))).code());
    }
    @Test void rejectsGapAndTerminalRollback() {
        EventInbox inbox = new EventInbox(); inbox.accept(event("e1", 1, RunEvent.State.RUNNING));
        assertEquals("RUNTIME_EVENT_GAP", assertThrows(RuntimeException.class, () -> inbox.accept(event("e3", 3, RunEvent.State.SUCCEEDED))).code());
        inbox.accept(event("e2", 2, RunEvent.State.SUCCEEDED));
        assertEquals("RUNTIME_STATE_CONFLICT", assertThrows(RuntimeException.class, () -> inbox.accept(event("e3", 3, RunEvent.State.RUNNING))).code());
    }
}
