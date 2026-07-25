package com.foodmate.api.controller;

import com.foodmate.application.account.UserAccountService;
import com.foodmate.application.runtime.RuntimeCancellationService;
import com.foodmate.application.runtime.V1RuntimeEventService;
import com.foodmate.shared.api.ApiResponse;
import com.foodmate.shared.trace.TraceContextHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent-runs")
public class V1AgentRunController extends AuthenticatedControllerSupport {
    private final V1RuntimeEventService events;
    private final RuntimeCancellationService cancellations;

    public V1AgentRunController(UserAccountService accounts, V1RuntimeEventService events, RuntimeCancellationService cancellations) {
        super(accounts); this.events = events; this.cancellations = cancellations;
    }

    @GetMapping("/{runId}")
    public ApiResponse<RunView> status(@PathVariable String runId, HttpServletRequest request) {
        events.requireRunOwner(runId, user(request).userId());
        var history = events.events(runId);
        return ApiResponse.success(new RunView(runId, events.status(runId), history.size()), TraceContextHolder.currentOrNew());
    }

    @PostMapping("/{runId}/cancel")
    public ApiResponse<RuntimeCancellationService.CancelResult> cancel(@PathVariable String runId, HttpServletRequest request, @Valid @RequestBody(required = false) CancelRequest body) {
        String reason = body == null || body.reason() == null || body.reason().isBlank() ? "user_requested" : body.reason();
        return ApiResponse.success(cancellations.request(user(request).userId(), runId, reason), TraceContextHolder.currentOrNew());
    }

    public record RunView(String runId, String status, int acceptedEventCount) {}
    public record CancelRequest(@NotBlank String reason) {}
}
