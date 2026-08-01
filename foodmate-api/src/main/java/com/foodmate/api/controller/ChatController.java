package com.foodmate.api.controller;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.foodmate.application.account.UserAccountService;
import com.foodmate.application.runtime.AgentRunCommandService;
import com.foodmate.application.runtime.RuntimeCancellationService;
import com.foodmate.application.runtime.RuntimeGatewayService;
import com.foodmate.application.runtime.V1RuntimeEventService;
import com.foodmate.shared.api.ApiResponse;
import com.foodmate.shared.id.IdGenerator;
import com.foodmate.shared.runtime.RunCommand;
import com.foodmate.shared.runtime.V1RunEvent;
import com.foodmate.shared.trace.TraceContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
public class ChatController {
    private final RuntimeGatewayService service;
    private final UserAccountService accounts;
    private final IdGenerator ids;
    private final AgentRunCommandService agentRuns;
    private final V1RuntimeEventService v1Events;
    private final RuntimeCancellationService cancellations;

    public ChatController(
            RuntimeGatewayService service,
            ObjectProvider<UserAccountService> accountProvider,
            ObjectProvider<IdGenerator> idProvider,
            ObjectProvider<AgentRunCommandService> agentRunProvider,
            ObjectProvider<V1RuntimeEventService> eventProvider,
            ObjectProvider<RuntimeCancellationService> cancellationProvider) {
        this.service = service;
        this.accounts = accountProvider.getIfAvailable();
        this.ids = idProvider.getIfAvailable();
        this.agentRuns = agentRunProvider.getIfAvailable();
        this.v1Events = eventProvider.getIfAvailable();
        this.cancellations = cancellationProvider.getIfAvailable();
    }

    @PostMapping("/runs")
    public ApiResponse<ChatRunResponse> createRun(
            HttpServletRequest servletRequest, @Valid @RequestBody ChatRunRequest request) {
        // The authenticated V1 path persists the run through AgentRunCommandService;
        // it does not need the fallback-only IdGenerator dependency here.
        boolean authenticated = accounts != null;
        if (authenticated && agentRuns != null) {
            var user = new AuthenticatedControllerSupport(accounts) {}.user(servletRequest);
            Long sessionId =
                    request.sessionId() == null || request.sessionId().isBlank()
                            ? accounts.createSession(
                                            user.userId(),
                                            request.prompt()
                                                    .substring(
                                                            0,
                                                            Math.min(
                                                                    80, request.prompt().length())),
                                            "agent")
                                    .sessionId()
                            : parseSessionId(request.sessionId());
            var creation =
                    agentRuns.createUserMessageRunDetails(
                            user.userId(),
                            sessionId,
                            request.prompt(),
                            TraceContextHolder.currentOrNew().traceId());
            return ApiResponse.success(
                    new ChatRunResponse(
                            creation.runId(),
                            creation.dispatchId(),
                            creation.status(),
                            false,
                            sessionId,
                            creation.message().messageId()),
                    TraceContextHolder.currentOrNew());
        }
        service.requireRuntimeAvailable();
        String runId = authenticated ? Long.toString(ids.nextId()) : "run_" + UUID.randomUUID();
        String dispatchId = "dispatch_" + UUID.randomUUID();
        Long sessionId = null;
        Long userMessageId = null;
        if (accounts != null) {
            var user = new AuthenticatedControllerSupport(accounts) {}.user(servletRequest);
            sessionId =
                    request.sessionId() == null || request.sessionId().isBlank()
                            ? accounts.createSession(
                                            user.userId(),
                                            request.prompt()
                                                    .substring(
                                                            0,
                                                            Math.min(
                                                                    80, request.prompt().length())),
                                            "agent")
                                    .sessionId()
                            : parseSessionId(request.sessionId());
            userMessageId =
                    accounts.addMessage(
                                    user.userId(),
                                    sessionId,
                                    "user",
                                    request.prompt(),
                                    null,
                                    authenticated ? Long.parseLong(runId) : null)
                            .messageId();
            if (authenticated)
                service.registerAgentRun(
                        runId,
                        user.userId(),
                        sessionId,
                        userMessageId,
                        TraceContextHolder.currentOrNew().traceId());
            else service.registerContext(runId, user.userId(), sessionId, userMessageId);
        }
        var result =
                service.dispatch(
                        new RunCommand(
                                dispatchId,
                                runId,
                                request.prompt(),
                                Instant.now().plusSeconds(60),
                                1));
        return ApiResponse.success(
                new ChatRunResponse(
                        runId,
                        dispatchId,
                        result.status().name(),
                        result.duplicate(),
                        sessionId,
                        userMessageId),
                TraceContextHolder.currentOrNew());
    }

    @GetMapping("/runs/{runId}")
    public ApiResponse<?> status(@PathVariable String runId, HttpServletRequest request) {
        requireOwner(runId, request);
        if (isV1(runId))
            return ApiResponse.success(
                    new ChatStatusResponse(runId, v1Events.status(runId)),
                    TraceContextHolder.currentOrNew());
        return ApiResponse.success(service.status(runId), TraceContextHolder.currentOrNew());
    }

    @GetMapping("/runs/{runId}/events")
    public ApiResponse<?> events(@PathVariable String runId, HttpServletRequest request) {
        requireOwner(runId, request);
        if (isV1(runId))
            return ApiResponse.success(
                    v1Events.events(runId).stream().map(ChatController::toChatEvent).toList(),
                    TraceContextHolder.currentOrNew());
        return ApiResponse.success(service.events(runId), TraceContextHolder.currentOrNew());
    }

    @PostMapping("/runs/{runId}/cancel")
    public ApiResponse<?> cancel(
            @PathVariable String runId,
            HttpServletRequest request,
            @RequestBody(required = false) CancelRunRequest body) {
        requireOwner(runId, request);
        String reason =
                body == null || body.reason() == null || body.reason().isBlank()
                        ? "user_cancelled"
                        : body.reason();
        if (isV1(runId))
            return ApiResponse.success(
                    cancellations.request(
                            new AuthenticatedControllerSupport(accounts) {}.user(request).userId(),
                            runId,
                            reason),
                    TraceContextHolder.currentOrNew());
        return ApiResponse.success(
                service.cancel(
                        new com.foodmate.shared.runtime.CancelCommand(
                                "cancel_" + UUID.randomUUID(),
                                runId,
                                reason,
                                Instant.now().plusSeconds(30))),
                TraceContextHolder.currentOrNew());
    }

    public record ChatRunRequest(@NotBlank String prompt, String sessionId) {}

    public record CancelRunRequest(String reason) {}

    private static long parseSessionId(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("sessionId must be a numeric session id");
        }
    }

    private void requireOwner(String runId, HttpServletRequest request) {
        if (isV1(runId))
            v1Events.requireRunOwner(
                    runId, new AuthenticatedControllerSupport(accounts) {}.user(request).userId());
        else if (accounts != null)
            service.requireRunOwner(
                    runId, new AuthenticatedControllerSupport(accounts) {}.user(request).userId());
    }

    private boolean isV1(String runId) {
        return v1Events != null && runId.matches("\\d+") && v1Events.exists(runId);
    }

    private static ChatRunEvent toChatEvent(V1RunEvent event) {
        return new ChatRunEvent(
                event.eventId(),
                event.runId(),
                event.eventSeq(),
                stateFor(event.eventType()),
                event.payload(),
                event.occurredAt());
    }

    private static String stateFor(String eventType) {
        return switch (eventType) {
            case "run.accepted" -> "DISPATCHED";
            case "run.completed" -> "SUCCEEDED";
            case "run.failed" -> "FAILED";
            case "run.cancelled" -> "CANCELED";
            default -> "RUNNING";
        };
    }

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ChatRunResponse(
            String runId,
            String dispatchId,
            String status,
            boolean duplicate,
            Long sessionId,
            Long userMessageId) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ChatStatusResponse(String runId, String status) {}

    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ChatRunEvent(
            String eventId,
            String runId,
            long eventSeq,
            String state,
            Object payload,
            Instant occurredAt) {}
}
