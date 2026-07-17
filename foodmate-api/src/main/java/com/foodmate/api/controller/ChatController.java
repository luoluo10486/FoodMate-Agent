package com.foodmate.api.controller;

import com.foodmate.application.runtime.RuntimeGatewayService;
import com.foodmate.application.account.UserAccountService;
import com.foodmate.shared.api.ApiResponse;
import com.foodmate.shared.runtime.RunCommand;
import com.foodmate.shared.trace.TraceContextHolder;
import com.foodmate.shared.id.IdGenerator;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;

@RestController
@RequestMapping("/api/chat")
public class ChatController {
    private final RuntimeGatewayService service;
    private final UserAccountService accounts;
    private final IdGenerator ids;
    public ChatController(RuntimeGatewayService service, ObjectProvider<UserAccountService> accountProvider, ObjectProvider<IdGenerator> idProvider) { this.service = service; this.accounts = accountProvider.getIfAvailable(); this.ids = idProvider.getIfAvailable(); }

    @PostMapping("/runs")
    public ApiResponse<ChatRunResponse> createRun(@RequestHeader(value = "Authorization", required = false) String authorization, @Valid @RequestBody ChatRunRequest request) {
        boolean authenticated = accounts != null && authorization != null && authorization.startsWith("Bearer ") && ids != null;
        String runId = authenticated ? Long.toString(ids.nextId()) : "run_" + UUID.randomUUID();
        String dispatchId = "dispatch_" + UUID.randomUUID();
        Long sessionId = null;
        Long userMessageId = null;
        if (accounts != null && authorization != null && authorization.startsWith("Bearer ")) {
            var user = accounts.requireUser(authorization.substring(7));
            sessionId = request.sessionId() == null || request.sessionId().isBlank()
                    ? accounts.createSession(user.userId(), request.prompt().substring(0, Math.min(80, request.prompt().length())), "agent").sessionId()
                    : parseSessionId(request.sessionId());
            userMessageId = accounts.addMessage(user.userId(), sessionId, "user", request.prompt(), null, authenticated ? Long.parseLong(runId) : null).messageId();
            if (authenticated) service.registerAgentRun(runId, user.userId(), sessionId, userMessageId, TraceContextHolder.currentOrNew().traceId());
            else service.registerContext(runId, user.userId(), sessionId, userMessageId);
        }
        var result = service.dispatch(new RunCommand(dispatchId, runId, request.prompt(), Instant.now().plusSeconds(60), 1));
        return ApiResponse.success(new ChatRunResponse(runId, dispatchId, result.status().name(), result.duplicate(), sessionId, userMessageId), TraceContextHolder.currentOrNew());
    }

    @GetMapping("/runs/{runId}")
    public ApiResponse<RuntimeGatewayService.StatusResult> status(@PathVariable String runId) {
        return ApiResponse.success(service.status(runId), TraceContextHolder.currentOrNew());
    }

    @GetMapping("/runs/{runId}/events")
    public ApiResponse<java.util.List<com.foodmate.shared.runtime.RunEvent>> events(@PathVariable String runId) {
        return ApiResponse.success(service.events(runId), TraceContextHolder.currentOrNew());
    }

    public record ChatRunRequest(@NotBlank String prompt, String sessionId) {}
    private static long parseSessionId(String value) { try { return Long.parseLong(value); } catch (NumberFormatException exception) { throw new IllegalArgumentException("sessionId must be a numeric session id"); } }
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ChatRunResponse(String runId, String dispatchId, String status, boolean duplicate, Long sessionId, Long userMessageId) {}
}
