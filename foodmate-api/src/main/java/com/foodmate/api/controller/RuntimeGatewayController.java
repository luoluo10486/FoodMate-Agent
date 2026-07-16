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
    public ApiResponse<RuntimeGatewayService.CommandResult> dispatch(@RequestHeader(value = "X-Runtime-Token", required = false) String token, @RequestHeader(value = "Authorization", required = false) String authorization, @Valid @RequestBody RunCommand command) {
        authenticate(token, authorization);
        return ApiResponse.success(service.dispatch(command), TraceContextHolder.currentOrNew());
    }

    @PostMapping("/internal/runtime/runs:cancel")
    public ApiResponse<RuntimeGatewayService.CommandResult> cancel(@RequestHeader(value = "X-Runtime-Token", required = false) String token, @RequestHeader(value = "Authorization", required = false) String authorization, @Valid @RequestBody CancelCommand command) {
        authenticate(token, authorization);
        return ApiResponse.success(service.cancel(command), TraceContextHolder.currentOrNew());
    }

    @PostMapping("/internal/runtime/runs:events")
    public ApiResponse<RuntimeGatewayService.EventResult> event(@RequestHeader(value = "X-Runtime-Token", required = false) String token, @RequestHeader(value = "Authorization", required = false) String authorization, @Valid @RequestBody RunEvent event) {
        authenticate(token, authorization);
        return ApiResponse.success(service.event(event), TraceContextHolder.currentOrNew());
    }

    private void authenticate(String token, String authorization) {
        String supplied = token;
        if ((supplied == null || supplied.isBlank()) && authorization != null && authorization.startsWith("Bearer ")) supplied = authorization.substring(7);
        if (!authToken.isBlank() && !authToken.equals(supplied)) throw new com.foodmate.shared.runtime.RuntimeException("RUNTIME_AUTH_INVALID", "invalid runtime token");
    }
}
