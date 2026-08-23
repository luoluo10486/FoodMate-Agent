package com.foodmate.api.controller.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodmate.api.controller.account.AuthenticatedControllerSupport;
import com.foodmate.application.account.service.UserAccountService;
import com.foodmate.application.runtime.service.V1RuntimeEventService;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.http.MediaType;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** 轮询持久化 SSE outbox，并向浏览器提供可恢复的事件流。 */
@RestController
@RequestMapping("/api/agent-runs")
public class V1RunStreamController extends AuthenticatedControllerSupport {
    private final V1RuntimeEventService events;
    private final TaskScheduler taskScheduler;
    private final ObjectMapper mapper = new ObjectMapper();

    public V1RunStreamController(
            UserAccountService accounts,
            V1RuntimeEventService events,
            TaskScheduler taskScheduler) {
        super(accounts);
        this.events = events;
        this.taskScheduler = taskScheduler;
    }

    @GetMapping(value = "/{runId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(
            @PathVariable String runId,
            @RequestHeader(value = "Last-Event-ID", required = false) String header,
            @RequestParam(value = "lastEventId", required = false) String query,
            HttpServletRequest request) {
        requireOwner(runId, request);
        // Last-Event-ID 可能是稳定的 sse_event_id，服务层会把它映射为真实 stream_seq。
        long after = events.cursorFor(runId, header == null ? query : header);
        SseEmitter emitter = new SseEmitter(120_000L);
        AtomicBoolean closed = new AtomicBoolean();
        final long[] cursor = {after};
        java.util.concurrent.atomic.AtomicReference<ScheduledFuture<?>> taskReference =
                new java.util.concurrent.atomic.AtomicReference<>();
        ScheduledFuture<?> task =
                taskScheduler.scheduleWithFixedDelay(
                        () -> {
                            if (closed.get()) return;
                            try {
                                for (var event : events.sseEvents(runId, cursor[0])) {
                                    // 发送成功后才推进内存游标；断线时仍可从数据库游标重新补发。
                                    emitter.send(
                                            SseEmitter.event()
                                                    .id(event.sseEventId())
                                                    .name(event.eventType())
                                                    .data(event.payload()));
                                    cursor[0] = event.streamSeq();
                                    if (event.terminal()) {
                                        closed.set(true);
                                        emitter.complete();
                                        ScheduledFuture<?> scheduled = taskReference.get();
                                        if (scheduled != null) scheduled.cancel(false);
                                        return;
                                    }
                                }
                            } catch (IOException exception) {
                                closed.set(true);
                                emitter.completeWithError(exception);
                                ScheduledFuture<?> scheduled = taskReference.get();
                                if (scheduled != null) scheduled.cancel(false);
                            } catch (RuntimeException exception) {
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

    private void requireOwner(String runId, HttpServletRequest request) {
        long numeric;
        try {
            numeric = Long.parseLong(runId);
        } catch (NumberFormatException exception) {
            throw new com.foodmate.shared.runtime.RuntimeException(
                    "RUNTIME_STATE_CONFLICT", "run id is invalid");
        }
        if (events == null || numeric < 1)
            throw new com.foodmate.shared.runtime.RuntimeException(
                    "RUNTIME_STATE_CONFLICT", "run not found");
        var user = user(request);
        if (user == null)
            throw new com.foodmate.shared.error.BusinessException(
                    com.foodmate.shared.error.ErrorCode.AUTH_REQUIRED);
        events.requireRunOwner(runId, user.userId());
    }
}
