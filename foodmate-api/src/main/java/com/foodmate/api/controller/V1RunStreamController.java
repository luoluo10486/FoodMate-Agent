package com.foodmate.api.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodmate.application.account.UserAccountService;
import com.foodmate.application.runtime.V1RuntimeEventService;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Polls durable SSE outbox rows and exposes them as a resumable browser stream. */
@RestController
@RequestMapping("/api/agent-runs")
public class V1RunStreamController extends AuthenticatedControllerSupport {
    private final V1RuntimeEventService events;
    private final ObjectMapper mapper = new ObjectMapper();

    public V1RunStreamController(UserAccountService accounts, V1RuntimeEventService events) {
        super(accounts); this.events = events;
    }

    @GetMapping(value = "/{runId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable String runId,
                             @RequestHeader(value = "Last-Event-ID", required = false) String header,
                             @RequestParam(value = "lastEventId", required = false) String query,
                             HttpServletRequest request) {
        requireOwner(runId, request);
        long after = events.cursorFor(runId, header == null ? query : header);
        SseEmitter emitter = new SseEmitter(120_000L);
        AtomicBoolean closed = new AtomicBoolean();
        var executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "foodmate-sse-" + runId);
            thread.setDaemon(true); return thread;
        });
        final long[] cursor = {after};
        var task = executor.scheduleWithFixedDelay(() -> {
            if (closed.get()) return;
            try {
                for (var event : events.sseEvents(runId, cursor[0])) {
                    emitter.send(SseEmitter.event().id(event.sseEventId()).name(event.eventType()).data(event.payload()));
                    cursor[0] = event.streamSeq();
                    if (event.terminal()) { closed.set(true); emitter.complete(); executor.shutdown(); return; }
                }
            } catch (IOException exception) {
                closed.set(true); emitter.completeWithError(exception); executor.shutdown();
            } catch (RuntimeException exception) {
                closed.set(true); emitter.completeWithError(exception); executor.shutdown();
            }
        }, 0, 200, TimeUnit.MILLISECONDS);
        emitter.onCompletion(() -> { closed.set(true); task.cancel(false); executor.shutdown(); });
        emitter.onTimeout(() -> { closed.set(true); task.cancel(false); executor.shutdown(); });
        return emitter;
    }

    private void requireOwner(String runId, HttpServletRequest request) {
        long numeric;
        try { numeric = Long.parseLong(runId); } catch (NumberFormatException exception) { throw new com.foodmate.shared.runtime.RuntimeException("RUNTIME_STATE_CONFLICT", "run id is invalid"); }
        if (events == null || numeric < 1) throw new com.foodmate.shared.runtime.RuntimeException("RUNTIME_STATE_CONFLICT", "run not found");
        var user = user(request);
        if (user == null) throw new com.foodmate.shared.error.BusinessException(com.foodmate.shared.error.ErrorCode.AUTH_REQUIRED);
        events.requireRunOwner(runId, user.userId());
    }

}
