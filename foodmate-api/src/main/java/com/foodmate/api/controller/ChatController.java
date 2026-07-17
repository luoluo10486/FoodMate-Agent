package com.foodmate.api.controller;

import com.foodmate.application.runtime.RuntimeGatewayService;
import com.foodmate.shared.api.ApiResponse;
import com.foodmate.shared.runtime.RunCommand;
import com.foodmate.shared.trace.TraceContextHolder;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.UUID;
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
    public ChatController(RuntimeGatewayService service) { this.service = service; }

    @PostMapping("/runs")
    public ApiResponse<ChatRunResponse> createRun(@Valid @RequestBody ChatRunRequest request) {
        String runId = "run_" + UUID.randomUUID();
        String dispatchId = "dispatch_" + UUID.randomUUID();
        var result = service.dispatch(new RunCommand(dispatchId, runId, request.prompt(), Instant.now().plusSeconds(60), 1));
        return ApiResponse.success(new ChatRunResponse(runId, dispatchId, result.status().name(), result.duplicate()), TraceContextHolder.currentOrNew());
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
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record ChatRunResponse(String runId, String dispatchId, String status, boolean duplicate) {}
}
