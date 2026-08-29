package com.foodmate.shared.runtime;

import java.util.HashMap;
import java.util.Map;

/**
 * 有序 Runtime 事件的内存兼容守卫。
 *
 * <p>持久化 Java Inbox 会在 PostgreSQL 中执行相同校验。该共享类型用于让旧网关契约在单元 测试和兼容适配器中保持确定性。
 */
public final class EventInbox {
    private final Map<String, String> fingerprints = new HashMap<>();
    private final Map<String, RunEvent> latest = new HashMap<>();

    /** 接受一次事件，并拒绝冲突、乱序或存在间隔的事件。 */
    public synchronized Result accept(RunEvent event) {
        String key = event.runId() + ":" + event.eventId();
        String fingerprint =
                event.eventSeq() + "|" + event.state() + "|" + String.valueOf(event.payload());
        if (fingerprints.containsKey(key)) {
            if (!fingerprints.get(key).equals(fingerprint))
                throw error("RUNTIME_EVENT_IDEMPOTENCY_CONFLICT");
            return Result.DUPLICATE;
        }
        RunEvent previous = latest.get(event.runId());
        if (previous != null) {
            if (event.eventSeq() <= previous.eventSeq()) throw error("RUNTIME_EVENT_OUT_OF_ORDER");
            if (event.eventSeq() > previous.eventSeq() + 1) throw error("RUNTIME_EVENT_GAP");
            if (terminal(previous.state()) && previous.state() != event.state())
                throw error("RUNTIME_STATE_CONFLICT");
            if (!validTransition(previous.state(), event.state()))
                throw error("RUNTIME_STATE_CONFLICT");
        }
        fingerprints.put(key, fingerprint);
        latest.put(event.runId(), event);
        return Result.ACCEPTED;
    }

    /** 返回 AgentRun 最近接受的事件；没有接受事件时返回 {@code null}。 */
    public synchronized RunEvent latest(String runId) {
        return latest.get(runId);
    }

    private static boolean terminal(RunEvent.State state) {
        return state == RunEvent.State.SUCCEEDED
                || state == RunEvent.State.FAILED
                || state == RunEvent.State.CANCELED;
    }

    private static boolean validTransition(RunEvent.State from, RunEvent.State to) {
        return (from == RunEvent.State.DISPATCHED && to == RunEvent.State.RUNNING)
                || (from == RunEvent.State.RUNNING && terminal(to));
    }

    private static RuntimeException error(String code) {
        return new RuntimeException(code, code);
    }

    /** 向 Inbox 写入事件的结果。 */
    public enum Result {
        ACCEPTED,
        DUPLICATE
    }
}
