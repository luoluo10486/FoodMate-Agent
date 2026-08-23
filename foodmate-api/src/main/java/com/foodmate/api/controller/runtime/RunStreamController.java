package com.foodmate.api.controller.runtime;

import com.foodmate.api.controller.account.AuthenticatedControllerSupport;
import com.foodmate.application.account.service.UserAccountService;
import com.foodmate.application.runtime.service.RuntimeGatewayService;
import com.foodmate.application.runtime.service.V1RuntimeEventService;
import com.foodmate.shared.runtime.RunEvent;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Provides the Chat SSE compatibility stream and delegates durable replay to the event service. */
@RestController
public class RunStreamController {
    private final RuntimeGatewayService service;
    private final UserAccountService accounts;
    private final V1RuntimeEventService v1Events;
    private final TaskScheduler taskScheduler;

    public RunStreamController(
            RuntimeGatewayService service,
            ObjectProvider<UserAccountService> accountProvider,
            ObjectProvider<V1RuntimeEventService> eventProvider,
            TaskScheduler taskScheduler) {
        this.service = service;
        this.accounts = accountProvider.getIfAvailable();
        this.v1Events = eventProvider.getIfAvailable();
        this.taskScheduler = taskScheduler;
    }

    @GetMapping(
            value = "/api/chat/runs/{runId}/stream",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            @PathVariable String runId,
            @RequestHeader(value = "Last-Event-ID", required = false) String headerLastEventId,
            @RequestParam(value = "lastEventId", required = false) String queryLastEventId,
            HttpServletRequest request) {
        if (isV1Run(runId)) return streamV1(runId, headerLastEventId, queryLastEventId, request);
        if (accounts != null)
            service.requireRunOwner(
                    runId, new AuthenticatedControllerSupport(accounts) {}.user(request).userId());
        String lastEventId = headerLastEventId != null ? headerLastEventId : queryLastEventId;
        long afterSequence = parseSequence(lastEventId);
        SseEmitter emitter = new SseEmitter(0L);
        AtomicReference<Consumer<RunEvent>> reference = new AtomicReference<>();
        Consumer<RunEvent> listener =
                event -> {
                    try {
                        emitter.send(
                                SseEmitter.event()
                                        .id(Long.toString(event.eventSeq()))
                                        .name("run.event")
                                        .data(event));
                        if (terminal(event.state())) {
                            emitter.complete();
                            service.unsubscribe(runId, reference.get());
                        }
                    } catch (IOException exception) {
                        service.unsubscribe(runId, reference.get());
                        emitter.completeWithError(exception);
                    }
                };
        reference.set(listener);
        emitter.onCompletion(() -> service.unsubscribe(runId, listener));
        emitter.onTimeout(() -> service.unsubscribe(runId, listener));
        service.subscribe(runId, afterSequence, listener);
        return emitter;
    }

    private SseEmitter streamV1(
            String runId,
            String headerLastEventId,
            String queryLastEventId,
            HttpServletRequest request) {
        var current = new AuthenticatedControllerSupport(accounts) {}.user(request);
        v1Events.requireRunOwner(runId, current.userId());
        String cursor = headerLastEventId != null ? headerLastEventId : queryLastEventId;
        long after = v1Events.cursorFor(runId, cursor);
        SseEmitter emitter = new SseEmitter(120_000L);
        AtomicBoolean closed = new AtomicBoolean();
        final long[] currentSequence = {after};
        java.util.concurrent.atomic.AtomicReference<ScheduledFuture<?>> taskReference =
                new java.util.concurrent.atomic.AtomicReference<>();
        ScheduledFuture<?> task =
                taskScheduler.scheduleWithFixedDelay(
                        () -> {
                            if (closed.get()) return;
                            try {
                                for (var event : v1Events.sseEvents(runId, currentSequence[0])) {
                                    emitter.send(
                                            SseEmitter.event()
                                                    .id(event.sseEventId())
                                                    .name(event.eventType())
                                                    .data(event.payload()));
                                    currentSequence[0] = event.streamSeq();
                                    if (event.terminal()) {
                                        closed.set(true);
                                        emitter.complete();
                                        ScheduledFuture<?> scheduled = taskReference.get();
                                        if (scheduled != null) scheduled.cancel(false);
                                        return;
                                    }
                                }
                            } catch (IOException | RuntimeException exception) {
                                closed.set(true);
                                emitter.completeWithError(exception);
                                ScheduledFuture<?> scheduled = taskReference.get();
                                if (scheduled != null) scheduled.cancel(false);
                            }
                        },
                        Instant.now(),
                        Duration.ofMillis(200));
        taskReference.set(task);
        emitter.onCompletion(
                () -> {
                    closed.set(true);
                    task.cancel(false);
                });
        emitter.onTimeout(
                () -> {
                    closed.set(true);
                    task.cancel(false);
                });
        return emitter;
    }

    private boolean isV1Run(String runId) {
        return v1Events != null && runId.matches("\\d+") && v1Events.exists(runId);
    }

    private static long parseSequence(String value) {
        if (value == null || value.isBlank()) return 0;
        try {
            return Math.max(0, Long.parseLong(value));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    private static boolean terminal(RunEvent.State state) {
        return state == RunEvent.State.SUCCEEDED
                || state == RunEvent.State.FAILED
                || state == RunEvent.State.CANCELED;
    }
}
