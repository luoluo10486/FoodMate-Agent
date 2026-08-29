package com.foodmate.application.runtime.admission.impl;

import com.foodmate.application.runtime.admission.AgentAdmissionService;
import com.foodmate.application.runtime.admission.port.out.AdmissionCoordinationPort;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Agent 准入策略和许可生命周期的应用服务。 */
@Service
public class AgentAdmissionServiceImpl implements AgentAdmissionService {
    private final AdmissionCoordinationPort coordination;
    private final boolean enabled;
    private final int globalLimit;
    private final int userLimit;
    private final int queueLimit;
    private final Duration lease;
    private final Duration queueLease;

    public AgentAdmissionServiceImpl(
            ObjectProvider<AdmissionCoordinationPort> provider,
            @Value(
                            "${foodmate.runtime.admission.enabled:${FOODMATE_AGENT_ADMISSION_ENABLED:false}}")
                    boolean enabled,
            @Value("${FOODMATE_AGENT_MAX_ACTIVE_RUNS_GLOBAL:20}") int globalLimit,
            @Value("${FOODMATE_AGENT_MAX_ACTIVE_RUNS_PER_USER:2}") int userLimit,
            @Value("${FOODMATE_AGENT_MAX_QUEUED_RUNS_GLOBAL:100}") int queueLimit,
            @Value("${FOODMATE_AGENT_PERMIT_LEASE_SECONDS:180}") int leaseSeconds,
            @Value("${FOODMATE_AGENT_QUEUE_LEASE_SECONDS:3600}") int queueLeaseSeconds) {
        this.coordination = provider.getIfAvailable();
        this.enabled = enabled;
        this.globalLimit = positive(globalLimit, "globalLimit");
        this.userLimit = positive(userLimit, "userLimit");
        this.queueLimit = positive(queueLimit, "queueLimit");
        this.lease = Duration.ofSeconds(positive(leaseSeconds, "leaseSeconds"));
        this.queueLease = Duration.ofSeconds(positive(queueLeaseSeconds, "queueLeaseSeconds"));
    }

    @Override
    public Admission admit(String runId, long userId, long sessionId) {
        return admit(runId, userId, sessionId, 0);
    }

    @Override
    public Admission admit(String runId, long userId, long sessionId, int priority) {
        if (!enabled) return new Admission(State.ACTIVE, List.of());
        requireCoordination();
        try {
            AdmissionCoordinationPort.AcquireResult result =
                    coordination.acquire(
                            new AdmissionCoordinationPort.AcquireRequest(
                                    runId,
                                    userId,
                                    sessionId,
                                    Math.max(0, priority),
                                    globalLimit,
                                    userLimit,
                                    queueLimit,
                                    lease,
                                    queueLease,
                                    Instant.now()));
            if (result == null || result.state() == null)
                throw runtime(
                        "RUNTIME_COORDINATION_UNAVAILABLE", "Agent admission coordination failed");
            return switch (result.state()) {
                case ACTIVE -> new Admission(State.ACTIVE, List.of());
                case QUEUED -> new Admission(State.QUEUED, List.of());
                case CAPACITY ->
                        throw runtime("RUNTIME_CAPACITY_EXCEEDED", "Agent admission queue is full");
            };
        } catch (RuntimeException exception) {
            throw coordinationFailure(exception);
        }
    }

    @Override
    public List<String> releaseAndPromote(String runId) {
        if (!enabled) return List.of();
        requireCoordination();
        try {
            return coordination.releaseAndPromote(
                    new AdmissionCoordinationPort.ReleaseRequest(
                            runId, globalLimit, userLimit, lease, Instant.now(), 10));
        } catch (RuntimeException exception) {
            throw coordinationFailure(exception);
        }
    }

    @Override
    public void renewActiveLeases() {
        if (!enabled) return;
        requireCoordination();
        try {
            coordination.renewActiveLeases(
                    new AdmissionCoordinationPort.RenewRequest(lease, Instant.now()));
        } catch (RuntimeException exception) {
            throw coordinationFailure(exception);
        }
    }

    @Override
    public void requireCoordination() {
        if (coordination == null)
            throw runtime(
                    "RUNTIME_COORDINATION_UNAVAILABLE",
                    "Agent admission coordination is unavailable");
        coordination.requireAvailable();
    }

    @Override
    public boolean isActive(String runId) {
        if (!enabled) return true;
        requireCoordination();
        try {
            return coordination.isActive(runId);
        } catch (RuntimeException exception) {
            throw coordinationFailure(exception);
        }
    }

    private com.foodmate.shared.runtime.RuntimeException coordinationFailure(
            RuntimeException exception) {
        if (exception instanceof com.foodmate.shared.runtime.RuntimeException runtimeException)
            return runtimeException;
        return runtime(
                "RUNTIME_COORDINATION_UNAVAILABLE", "Agent admission coordination is unavailable");
    }

    private static int positive(int value, String name) {
        if (value <= 0) throw new IllegalArgumentException(name + " must be positive");
        return value;
    }

    private static com.foodmate.shared.runtime.RuntimeException runtime(
            String code, String message) {
        return new com.foodmate.shared.runtime.RuntimeException(code, message);
    }
}
