package com.foodmate.api.controller;

import com.foodmate.application.runtime.RuntimeGatewayService;
import com.foodmate.shared.api.ApiResponse;
import com.foodmate.shared.runtime.CancelCommand;
import com.foodmate.shared.runtime.RunCommand;
import com.foodmate.shared.runtime.RunEvent;
import com.foodmate.shared.trace.TraceContextHolder;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RequestHeader;

@RestController
public class RuntimeGatewayController {
    private final RuntimeGatewayService service;
    private final String authToken;
    public RuntimeGatewayController(RuntimeGatewayService service, @Value("${foodmate.runtime.auth-token:}") String authToken) { this.service = service; this.authToken = authToken; }

    @PostMapping("/internal/runtime/runs:dispatch")
    public ApiResponse<RuntimeGatewayService.CommandResult> dispatch(@RequestHeader(value = "X-Runtime-Token", required = false) String token, @Valid @RequestBody RunCommand command) {
        authenticate(token);
        return ApiResponse.success(service.dispatch(command), TraceContextHolder.currentOrNew());
    }

    @PostMapping("/internal/runtime/runs:cancel")
    public ApiResponse<RuntimeGatewayService.CommandResult> cancel(@RequestHeader(value = "X-Runtime-Token", required = false) String token, @Valid @RequestBody CancelCommand command) {
        authenticate(token);
        return ApiResponse.success(service.cancel(command), TraceContextHolder.currentOrNew());
    }

    @PostMapping("/internal/runtime/runs:events")
    public ApiResponse<RuntimeGatewayService.EventResult> event(@RequestHeader(value = "X-Runtime-Token", required = false) String token, @Valid @RequestBody RunEvent event) {
        authenticate(token);
        return ApiResponse.success(service.event(event), TraceContextHolder.currentOrNew());
    }

    private void authenticate(String token) {
        if (!authToken.isBlank() && !authToken.equals(token)) throw new com.foodmate.shared.runtime.RuntimeException("RUNTIME_AUTH_INVALID", "invalid runtime token");
    }
}
