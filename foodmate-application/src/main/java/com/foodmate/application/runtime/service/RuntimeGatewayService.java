package com.foodmate.application.runtime.service;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.foodmate.shared.runtime.CancelCommand;
import com.foodmate.shared.runtime.RunCommand;
import com.foodmate.shared.runtime.RunEvent;
import java.util.List;
import java.util.function.Consumer;

/** Coordinates runtime commands and events at the application boundary. */
public interface RuntimeGatewayService {
    CommandResult dispatch(RunCommand command);

    CommandResult cancel(CancelCommand command);

    EventResult event(RunEvent event);

    List<RunEvent> events(String runId);

    StatusResult status(String runId);

    void registerAgentRun(
            String runId, long userId, long sessionId, long userMessageId, String traceId);

    void registerContext(String runId, long userId, long sessionId, long userMessageId);

    void requireRunOwner(String runId, long userId);

    void subscribe(String runId, long afterSequence, Consumer<RunEvent> listener);

    void unsubscribe(String runId, Consumer<RunEvent> listener);

    void requireRuntimeAvailable();

    enum Status {
        DISPATCHED,
        RUNNING,
        SUCCEEDED,
        FAILED,
        CANCELED
    }

    record CommandResult(String commandId, String runId, Status status, boolean duplicate) {}

    record EventResult(String eventId, String runId, Status status, boolean duplicate) {}

    enum AgentStatus {
        QUEUED("queued"),
        ROUTED("routed"),
        EXECUTING("executing"),
        COMPLETED("completed"),
        FAILED("failed"),
        CANCELLED("cancelled");

        private final String value;

        AgentStatus(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }

        public static AgentStatus from(String value) {
            return java.util.Arrays.stream(values())
                    .filter(status -> status.value.equals(value))
                    .findFirst()
                    .orElseThrow();
        }
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    record StatusResult(String runId, Status status, AgentStatus agentStatus) {}

    class IdempotencyConflict extends com.foodmate.shared.runtime.RuntimeException {
        public IdempotencyConflict(String message) {
            super(message, message);
        }
    }
}
