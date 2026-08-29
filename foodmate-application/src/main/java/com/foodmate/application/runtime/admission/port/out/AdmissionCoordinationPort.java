package com.foodmate.application.runtime.admission.port.out;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** 短期 Agent 许可的分布式准入协调边界。 */
public interface AdmissionCoordinationPort {
    AcquireResult acquire(AcquireRequest request);

    List<String> releaseAndPromote(ReleaseRequest request);

    void renewActiveLeases(RenewRequest request);

    boolean isActive(String runId);

    void requireAvailable();

    record AcquireRequest(
            String runId,
            long userId,
            long sessionId,
            int priority,
            int globalLimit,
            int userLimit,
            int queueLimit,
            Duration lease,
            Duration queueLease,
            Instant now) {}

    record ReleaseRequest(
            String runId,
            int globalLimit,
            int userLimit,
            Duration lease,
            Instant now,
            int maxCandidates) {}

    record RenewRequest(Duration lease, Instant now) {}

    record AcquireResult(State state) {
        public enum State {
            ACTIVE,
            QUEUED,
            CAPACITY
        }
    }
}
