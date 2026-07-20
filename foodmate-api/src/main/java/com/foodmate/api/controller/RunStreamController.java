package com.foodmate.api.controller;

import com.foodmate.application.runtime.RuntimeGatewayService;
import com.foodmate.shared.runtime.RunEvent;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import com.foodmate.application.account.UserAccountService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
public class RunStreamController {
    private final RuntimeGatewayService service;
    private final UserAccountService accounts;
    public RunStreamController(RuntimeGatewayService service, ObjectProvider<UserAccountService> accountProvider) { this.service = service; this.accounts = accountProvider.getIfAvailable(); }

    @GetMapping(value = "/api/chat/runs/{runId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable String runId, @RequestHeader(value = "Last-Event-ID", required = false) String headerLastEventId, @RequestParam(value = "lastEventId", required = false) String queryLastEventId, HttpServletRequest request) {
        if (accounts != null) service.requireRunOwner(runId, new AuthenticatedControllerSupport(accounts) {}.user(request).userId());
        String lastEventId = headerLastEventId != null ? headerLastEventId : queryLastEventId;
        long afterSequence = parseSequence(lastEventId);
        SseEmitter emitter = new SseEmitter(0L);
        AtomicReference<Consumer<RunEvent>> reference = new AtomicReference<>();
        Consumer<RunEvent> listener = event -> {
            try {
                emitter.send(SseEmitter.event().id(Long.toString(event.eventSeq())).name("run.event").data(event));
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

    private static long parseSequence(String value) {
        if (value == null || value.isBlank()) return 0;
        try { return Math.max(0, Long.parseLong(value)); }
        catch (NumberFormatException ignored) { return 0; }
    }

    private static boolean terminal(RunEvent.State state) {
        return state == RunEvent.State.SUCCEEDED || state == RunEvent.State.FAILED || state == RunEvent.State.CANCELED;
    }
}
