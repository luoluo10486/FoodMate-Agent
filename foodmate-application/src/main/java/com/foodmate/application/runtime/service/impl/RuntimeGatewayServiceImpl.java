package com.foodmate.application.runtime.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.foodmate.application.account.service.UserAccountService;
import com.foodmate.application.runtime.port.out.RuntimeGatewayPort;
import com.foodmate.application.runtime.port.out.RuntimeRepository;
import com.foodmate.application.runtime.service.RuntimeGatewayService;
import com.foodmate.shared.conversation.enums.MessageRole;
import com.foodmate.shared.error.BusinessException;
import com.foodmate.shared.error.ErrorCode;
import com.foodmate.shared.runtime.CancelCommand;
import com.foodmate.shared.runtime.EventInbox;
import com.foodmate.shared.runtime.RunCommand;
import com.foodmate.shared.runtime.RunEvent;
import com.foodmate.shared.runtime.RuntimeException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class RuntimeGatewayServiceImpl implements RuntimeGatewayService {
    private static final Logger log = LoggerFactory.getLogger(RuntimeGatewayServiceImpl.class);
    private final Map<String, String> dispatches = new HashMap<>();
    private final Map<String, String> cancels = new HashMap<>();
    private final Map<String, Status> statuses = new HashMap<>();
    private final Map<String, List<RunEvent>> eventHistory = new HashMap<>();
    private final Map<String, List<Consumer<RunEvent>>> listeners = new HashMap<>();
    private final EventInbox inbox = new EventInbox();
    private final RuntimeRepository store;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RuntimeGatewayPort gatewayClient;
    private final UserAccountService accounts;
    private final Map<String, RunContext> runContexts = new HashMap<>();
    private final Map<String, AgentStatus> agentStatuses = new HashMap<>();
    private final ScheduledExecutorService timeoutExecutor =
            new ScheduledThreadPoolExecutor(
                    1,
                    runnable -> {
                        Thread thread = new Thread(runnable, "foodmate-runtime-timeout");
                        thread.setDaemon(true);
                        return thread;
                    });

    public RuntimeGatewayServiceImpl() {
        this.store = null;
        this.gatewayClient =
                new RuntimeGatewayPort() {
                    @Override
                    public Response dispatch(RunCommand command) {
                        return new Response(202, "{}");
                    }

                    @Override
                    public Response cancel(CancelCommand command) {
                        return new Response(202, "{}");
                    }
                };
        this.accounts = null;
    }

    @Autowired
    public RuntimeGatewayServiceImpl(
            ObjectProvider<RuntimeRepository> storeProvider,
            ObjectProvider<RuntimeGatewayPort> gatewayProvider,
            ObjectProvider<UserAccountService> accountProvider) {
        this.store = storeProvider.getIfAvailable();
        this.gatewayClient = gatewayProvider.getIfAvailable();
        this.accounts = accountProvider.getIfAvailable();
    }

    @Override
    public synchronized CommandResult dispatch(RunCommand command) {
        if (command.deadlineAt().isBefore(Instant.now())) {
            throw new RuntimeException("RUNTIME_DEADLINE_EXCEEDED", "dispatch deadline exceeded");
        }
        requireRuntimeAvailable();
        String fingerprint =
                command.runId()
                        + "|"
                        + command.input()
                        + "|"
                        + command.deadlineAt()
                        + "|"
                        + command.attempt();
        CommandResult result;
        if (store != null) {
            result = dispatchJdbc(command, fingerprint);
        } else {
            String previous = dispatches.putIfAbsent(command.dispatchId(), fingerprint);
            if (previous != null && !previous.equals(fingerprint)) {
                throw new IdempotencyConflict("RUNTIME_DISPATCH_IDEMPOTENCY_CONFLICT");
            }
            if (previous != null) {
                return new CommandResult(
                        command.dispatchId(),
                        command.runId(),
                        statuses.getOrDefault(command.runId(), Status.DISPATCHED),
                        true);
            }
            statuses.putIfAbsent(command.runId(), Status.DISPATCHED);
            result =
                    new CommandResult(
                            command.dispatchId(), command.runId(), Status.DISPATCHED, false);
        }
        if (!result.duplicate() && gatewayClient != null) {
            gatewayClient.dispatch(command);
        }
        scheduleTimeout(command);
        return result;
    }

    @Override
    public synchronized CommandResult cancel(CancelCommand command) {
        if (command.deadlineAt().isBefore(Instant.now())) {
            throw new RuntimeException("RUNTIME_DEADLINE_EXCEEDED", "cancel deadline exceeded");
        }
        requireRuntimeAvailable();
        String fingerprint = command.runId() + "|" + command.reason() + "|" + command.deadlineAt();
        if (store != null) {
            CommandResult result = cancelJdbc(command, fingerprint);
            if (!result.duplicate()
                    && gatewayClient != null
                    && result.status() != Status.SUCCEEDED
                    && result.status() != Status.FAILED) {
                gatewayClient.cancel(command);
            }
            return result;
        }
        String previous = cancels.putIfAbsent(command.cancelId(), fingerprint);
        if (previous != null && !previous.equals(fingerprint)) {
            throw new IdempotencyConflict("RUNTIME_CANCEL_IDEMPOTENCY_CONFLICT");
        }
        Status current = statuses.get(command.runId());
        if (current == null) {
            throw new IllegalArgumentException("runId does not exist");
        }
        if (previous == null && current != Status.SUCCEEDED && current != Status.FAILED) {
            statuses.put(command.runId(), Status.CANCELED);
            updateAgentStatus(command.runId(), AgentStatus.CANCELLED, null);
            RunEvent canceled =
                    new RunEvent(
                            "cancel-" + command.cancelId(),
                            command.runId(),
                            nextSequence(command.runId()),
                            RunEvent.State.CANCELED,
                            objectMapper.createObjectNode().put("reason", command.reason()),
                            Instant.now());
            eventHistory
                    .computeIfAbsent(command.runId(), ignored -> new ArrayList<>())
                    .add(canceled);
            publish(canceled);
            if (gatewayClient != null) {
                gatewayClient.cancel(command);
            }
        }
        return new CommandResult(
                command.cancelId(),
                command.runId(),
                statuses.get(command.runId()),
                previous != null);
    }

    @Override
    public synchronized EventResult event(RunEvent event) {
        if (store != null) return eventJdbc(event);
        if (!statuses.containsKey(event.runId())) {
            throw new IllegalArgumentException("runId does not exist");
        }
        EventInbox.Result result = inbox.accept(event);
        if (result == EventInbox.Result.ACCEPTED) {
            statuses.put(event.runId(), toStatus(event.state()));
            updateAgentStatus(event.runId(), toAgentStatus(event.state()), event.payload());
            eventHistory.computeIfAbsent(event.runId(), ignored -> new ArrayList<>()).add(event);
            publish(event);
            persistAssistantAnswer(event);
        }
        return new EventResult(
                event.eventId(),
                event.runId(),
                statuses.get(event.runId()),
                result == EventInbox.Result.DUPLICATE);
    }

    @Override
    public synchronized java.util.List<RunEvent> events(String runId) {
        if (store == null)
            return java.util.List.copyOf(eventHistory.getOrDefault(runId, java.util.List.of()));
        if (statusJdbc(runId) == null) throw new IllegalArgumentException("runId does not exist");
        return store.events(runId).stream()
                .map(
                        row ->
                                new RunEvent(
                                        row.id(),
                                        runId,
                                        row.seq(),
                                        RunEvent.State.valueOf(row.state()),
                                        readPayload(row.payload()),
                                        row.occurredAt()))
                .toList();
    }

    @Override
    public synchronized StatusResult status(String runId) {
        Status status = store == null ? statuses.get(runId) : statusJdbc(runId);
        if (status == null) throw new IllegalArgumentException("runId does not exist");
        return new StatusResult(runId, status, agentStatus(runId));
    }

    @Override
    public synchronized void registerAgentRun(
            String runId, long userId, long sessionId, long userMessageId, String traceId) {
        AgentStatus status = AgentStatus.QUEUED;
        agentStatuses.put(runId, status);
        if (store != null)
            store.registerAgentRun(
                    Long.parseLong(runId),
                    sessionId,
                    userMessageId,
                    status.value(),
                    traceId,
                    userId);
        runContexts.put(
                runId, new RunContext(userId, sessionId, userMessageId, Long.parseLong(runId)));
    }

    @Override
    public synchronized void registerContext(
            String runId, long userId, long sessionId, long userMessageId) {
        runContexts.put(runId, new RunContext(userId, sessionId, userMessageId, null));
    }

    /** 确保用户只能查看或控制自己会话创建的运行记录。 */
    @Override
    public synchronized void requireRunOwner(String runId, long userId) {
        RunContext context = runContexts.get(runId);
        if (context != null) {
            if (context.userId() != userId) {
                throw new BusinessException(ErrorCode.FORBIDDEN);
            }
            return;
        }
        if (store != null && runId.matches("\\d+")) {
            Long owner = store.owner(Long.parseLong(runId));
            if (owner != null && owner == userId) {
                return;
            }
        }
        throw new BusinessException(ErrorCode.FORBIDDEN);
    }

    /** 注册监听器，并先回放指定序号之后的事件，再接收实时事件。 */
    @Override
    public synchronized void subscribe(
            String runId, long afterSequence, Consumer<RunEvent> listener) {
        if (!statuses.containsKey(runId) && store == null && !runExistsJdbc(runId)) {
            throw new IllegalArgumentException("runId does not exist");
        }
        for (RunEvent event : events(runId))
            if (event.eventSeq() > afterSequence) {
                listener.accept(event);
            }
        listeners.computeIfAbsent(runId, ignored -> new ArrayList<>()).add(listener);
    }

    @Override
    public synchronized void unsubscribe(String runId, Consumer<RunEvent> listener) {
        List<Consumer<RunEvent>> current = listeners.get(runId);
        if (current != null) {
            current.remove(listener);
            if (current.isEmpty()) listeners.remove(runId);
        }
    }

    private void publish(RunEvent event) {
        List<Consumer<RunEvent>> current = listeners.get(event.runId());
        if (current == null) return;
        for (Consumer<RunEvent> listener : List.copyOf(current)) {
            try {
                listener.accept(event);
            } catch (RuntimeException exception) {
                log.warn(
                        "runtime event listener failed and was removed: runId={}",
                        event.runId(),
                        exception);
                current.remove(listener);
            }
        }
    }

    private long nextSequence(String runId) {
        return eventHistory.getOrDefault(runId, List.of()).stream()
                        .mapToLong(RunEvent::eventSeq)
                        .max()
                        .orElse(0)
                + 1;
    }

    private void scheduleTimeout(RunCommand command) {
        long delay =
                Math.max(1, command.deadlineAt().toEpochMilli() - Instant.now().toEpochMilli());
        timeoutExecutor.schedule(() -> timeout(command), delay, TimeUnit.MILLISECONDS);
    }

    @Override
    public void requireRuntimeAvailable() {
        if (store != null && gatewayClient == null) {
            throw new RuntimeException("RUNTIME_UNAVAILABLE", "runtime is not configured");
        }
    }

    private void timeout(RunCommand command) {
        synchronized (this) {
            Status current =
                    store == null ? statuses.get(command.runId()) : statusJdbc(command.runId());
            if (current == null
                    || current == Status.SUCCEEDED
                    || current == Status.FAILED
                    || current == Status.CANCELED) {
                return;
            }
        }
        try {
            cancel(
                    new CancelCommand(
                            "timeout-" + command.dispatchId(),
                            command.runId(),
                            "deadline_exceeded",
                            Instant.now().plusSeconds(30)));
        } catch (RuntimeException exception) {
            log.warn(
                    "runtime timeout cancellation failed: runId={}, dispatchId={}",
                    command.runId(),
                    command.dispatchId(),
                    exception);
        }
    }

    private boolean runExistsJdbc(String runId) {
        return store != null && store.runExists(runId);
    }

    private CommandResult dispatchJdbc(RunCommand command, String fingerprint) {
        String old = store.dispatchFingerprint(command.dispatchId());
        if (old != null) {
            if (!old.equals(fingerprint)) {
                throw new IdempotencyConflict("RUNTIME_DISPATCH_IDEMPOTENCY_CONFLICT");
            }
            return new CommandResult(
                    command.dispatchId(), command.runId(), statusJdbc(command.runId()), true);
        }
        store.createRun(command.runId(), Status.DISPATCHED.name());
        store.insertDispatch(command.dispatchId(), command.runId(), fingerprint);
        return new CommandResult(command.dispatchId(), command.runId(), Status.DISPATCHED, false);
    }

    private CommandResult cancelJdbc(CancelCommand command, String fingerprint) {
        String old = store.cancelFingerprint(command.cancelId());
        if (old != null) {
            if (!old.equals(fingerprint)) {
                throw new IdempotencyConflict("RUNTIME_CANCEL_IDEMPOTENCY_CONFLICT");
            }
            return new CommandResult(
                    command.cancelId(), command.runId(), statusJdbc(command.runId()), true);
        }
        Status current = statusJdbc(command.runId());
        if (current == null) {
            throw new IllegalArgumentException("runId does not exist");
        }
        store.insertCancel(command.cancelId(), command.runId(), fingerprint);
        if (current != Status.SUCCEEDED && current != Status.FAILED) {
            store.updateStatus(command.runId(), Status.CANCELED.name());
            current = Status.CANCELED;
        }
        return new CommandResult(command.cancelId(), command.runId(), current, false);
    }

    private EventResult eventJdbc(RunEvent event) {
        String fingerprint =
                event.eventSeq() + "|" + event.state() + "|" + String.valueOf(event.payload());
        Status current = statusJdbc(event.runId());
        if (current == null) {
            throw new IllegalArgumentException("runId does not exist");
        }
        String known = store.eventFingerprint(event.runId(), event.eventId());
        if (known != null) {
            if (!fingerprint.equals(known)) {
                throw new RuntimeException(
                        "RUNTIME_EVENT_IDEMPOTENCY_CONFLICT", "event fingerprint conflict");
            }
            return new EventResult(event.eventId(), event.runId(), current, true);
        }
        RuntimeRepository.EventHead latest = store.latestEvent(event.runId());
        if (latest != null) {
            long previousSeq = latest.seq();
            RunEvent.State previous = RunEvent.State.valueOf(latest.state());
            if (event.eventSeq() <= previousSeq) {
                throw new RuntimeException(
                        "RUNTIME_EVENT_OUT_OF_ORDER", "event sequence out of order");
            }
            if (event.eventSeq() > previousSeq + 1) {
                throw new RuntimeException("RUNTIME_EVENT_GAP", "event sequence gap");
            }
            if ((previous == RunEvent.State.DISPATCHED && event.state() != RunEvent.State.RUNNING)
                    || (previous == RunEvent.State.RUNNING && !terminal(event.state())))
                throw new RuntimeException("RUNTIME_STATE_CONFLICT", "invalid state transition");
        }
        store.insertEvent(event, fingerprint, payloadJson(event.payload()));
        store.updateStatus(event.runId(), event.state().name());
        updateAgentStatus(event.runId(), toAgentStatus(event.state()), event.payload());
        publish(event);
        persistAssistantAnswer(event);
        return new EventResult(event.eventId(), event.runId(), toStatus(event.state()), false);
    }

    private void persistAssistantAnswer(RunEvent event) {
        if (accounts == null || !terminal(event.state()) || event.payload() == null) {
            return;
        }
        JsonNode payload = objectMapper.valueToTree(event.payload());
        if (!payload.isObject()) {
            return;
        }
        JsonNode answer = payload.get("answer");
        RunContext context = runContexts.get(event.runId());
        if (answer != null && !answer.isNull() && context != null) {
            accounts.addMessage(
                    context.userId(),
                    context.sessionId(),
                    MessageRole.ASSISTANT.code(),
                    answer.asText(),
                    payload,
                    context.agentRunId());
        }
    }

    private void updateAgentStatus(String runId, AgentStatus status, Object payload) {
        agentStatuses.put(runId, status);
        if (store != null && runId.matches("\\d+")) {
            String error =
                    status == AgentStatus.FAILED
                            ? "RUNTIME_FAILED"
                            : status == AgentStatus.CANCELLED ? "RUNTIME_CANCELED" : null;
            store.updateAgentRun(
                    Long.parseLong(runId),
                    status.value(),
                    payloadJson(
                            payload == null
                                    ? JsonNodeFactory.instance.objectNode()
                                    : objectMapper.valueToTree(payload)),
                    error);
        }
    }

    private AgentStatus agentStatus(String runId) {
        if (agentStatuses.containsKey(runId)) {
            return agentStatuses.get(runId);
        }
        if (store != null && runId.matches("\\d+")) {
            String value = store.agentStatus(Long.parseLong(runId));
            return value == null ? null : AgentStatus.from(value);
        }
        return null;
    }

    private Status statusJdbc(String runId) {
        String value = store.status(runId);
        return value == null ? null : Status.valueOf(value);
    }

    private String payloadJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new RuntimeException(
                    "RUNTIME_CONTRACT_INVALID", "event payload is not valid JSON");
        }
    }

    private Object readPayload(String payload) {
        try {
            return objectMapper.readTree(payload);
        } catch (JsonProcessingException exception) {
            log.warn(
                    "runtime event payload could not be decoded; returning raw payload", exception);
            return payload;
        }
    }

    private static boolean terminal(RunEvent.State state) {
        return state == RunEvent.State.SUCCEEDED
                || state == RunEvent.State.FAILED
                || state == RunEvent.State.CANCELED;
    }

    private static Status toStatus(RunEvent.State state) {
        return switch (state) {
            case DISPATCHED -> Status.DISPATCHED;
            case RUNNING -> Status.RUNNING;
            case SUCCEEDED -> Status.SUCCEEDED;
            case FAILED -> Status.FAILED;
            case CANCELED -> Status.CANCELED;
        };
    }

    private record RunContext(long userId, long sessionId, long userMessageId, Long agentRunId) {}

    private static AgentStatus toAgentStatus(RunEvent.State state) {
        return switch (state) {
            case DISPATCHED -> AgentStatus.QUEUED;
            case RUNNING -> AgentStatus.EXECUTING;
            case SUCCEEDED -> AgentStatus.COMPLETED;
            case FAILED -> AgentStatus.FAILED;
            case CANCELED -> AgentStatus.CANCELLED;
        };
    }
}
