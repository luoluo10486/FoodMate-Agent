package com.foodmate.shared.runtime;

import java.util.HashMap;
import java.util.Map;

public final class EventInbox {
    private final Map<String, String> fingerprints = new HashMap<>();
    private final Map<String, RunEvent> latest = new HashMap<>();

    public synchronized Result accept(RunEvent event) {
        String key = event.runId() + ":" + event.eventId();
        String fingerprint = event.eventSeq() + "|" + event.state() + "|" + String.valueOf(event.payload());
        if (fingerprints.containsKey(key)) {
            if (!fingerprints.get(key).equals(fingerprint)) throw error("RUNTIME_EVENT_IDEMPOTENCY_CONFLICT");
            return Result.DUPLICATE;
        }
        RunEvent previous = latest.get(event.runId());
        if (previous != null) {
            if (event.eventSeq() <= previous.eventSeq()) throw error("RUNTIME_EVENT_OUT_OF_ORDER");
            if (event.eventSeq() > previous.eventSeq() + 1) throw error("RUNTIME_EVENT_GAP");
            if (terminal(previous.state()) && previous.state() != event.state()) throw error("RUNTIME_STATE_CONFLICT");
            if (!validTransition(previous.state(), event.state())) throw error("RUNTIME_STATE_CONFLICT");
        }
        fingerprints.put(key, fingerprint); latest.put(event.runId(), event); return Result.ACCEPTED;
    }
    public synchronized RunEvent latest(String runId) { return latest.get(runId); }
    private static boolean terminal(RunEvent.State state) { return state == RunEvent.State.SUCCEEDED || state == RunEvent.State.FAILED || state == RunEvent.State.CANCELED; }
    private static boolean validTransition(RunEvent.State from, RunEvent.State to) {
        return (from == RunEvent.State.DISPATCHED && to == RunEvent.State.RUNNING)
                || (from == RunEvent.State.RUNNING && terminal(to));
    }
    private static RuntimeException error(String code) { return new RuntimeException(code, code); }
    public enum Result { ACCEPTED, DUPLICATE }
}
