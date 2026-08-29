package com.foodmate.application.runtime.admission;

import java.util.List;

/** 协调 AgentRun 的短期 Redis 准入租约。 */
public interface AgentAdmissionService {
    Admission admit(String runId, long userId, long sessionId);

    Admission admit(String runId, long userId, long sessionId, int priority);

    List<String> releaseAndPromote(String runId);

    void renewActiveLeases();

    void requireCoordination();

    boolean isActive(String runId);

    enum State {
        ACTIVE,
        QUEUED
    }

    record Admission(State state, List<String> promotedRunIds) {}
}
