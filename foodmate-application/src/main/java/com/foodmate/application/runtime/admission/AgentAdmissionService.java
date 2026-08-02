package com.foodmate.application.runtime.admission;

import java.util.List;

/** Coordinates short-lived Redis admission leases for agent runs. */
public interface AgentAdmissionService {
    Admission admit(String runId, long userId, long sessionId);

    Admission admit(String runId, long userId, long sessionId, int priority);

    List<String> releaseAndPromote(String runId);

    void renewActiveLeases();

    void requireRedis();

    boolean isActive(String runId);

    enum State {
        ACTIVE,
        QUEUED
    }

    record Admission(State state, List<String> promotedRunIds) {}
}
