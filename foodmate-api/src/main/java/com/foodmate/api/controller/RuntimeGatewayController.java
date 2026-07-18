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
    private final String contractVersion;
    public RuntimeGatewayController(RuntimeGatewayService service, @Value("${foodmate.runtime.auth-token:}") String authToken, @Value("${foodmate.runtime.contract-version:v1}") String contractVersion) { this.service = service; this.authToken = authToken; this.contractVersion = contractVersion; }

    @PostMapping("/internal/runtime/runs:dispatch")
    public ApiResponse<RuntimeGatewayService.CommandResult> dispatch(@RequestHeader(value = "X-Runtime-Token", required = false) String token, @RequestHeader(value = "Authorization", required = false) String authorization, @RequestHeader(value = "X-Contract-Version", required = false) String version, @Valid @RequestBody RunCommand command) {
        authenticate(token, authorization, version);
        return ApiResponse.success(service.dispatch(command), TraceContextHolder.currentOrNew());
    }

    @PostMapping("/internal/runtime/runs:cancel")
    public ApiResponse<RuntimeGatewayService.CommandResult> cancel(@RequestHeader(value = "X-Runtime-Token", required = false) String token, @RequestHeader(value = "Authorization", required = false) String authorization, @RequestHeader(value = "X-Contract-Version", required = false) String version, @Valid @RequestBody CancelCommand command) {
        authenticate(token, authorization, version);
        return ApiResponse.success(service.cancel(command), TraceContextHolder.currentOrNew());
    }

    @PostMapping("/internal/runtime/runs:events")
    public ApiResponse<RuntimeGatewayService.EventResult> event(@RequestHeader(value = "X-Runtime-Token", required = false) String token, @RequestHeader(value = "Authorization", required = false) String authorization, @RequestHeader(value = "X-Contract-Version", required = false) String version, @Valid @RequestBody RunEvent event) {
        authenticate(token, authorization, version);
        return ApiResponse.success(service.event(event), TraceContextHolder.currentOrNew());
    }

    private void authenticate(String token, String authorization, String version) {
        String supplied = token;
        if ((supplied == null || supplied.isBlank()) && authorization != null && authorization.startsWith("Bearer ")) supplied = authorization.substring(7);
        if (!authToken.isBlank() && !authToken.equals(supplied)) throw new com.foodmate.shared.runtime.RuntimeException("RUNTIME_AUTH_INVALID", "invalid runtime token");
        if (version != null && !contractVersion.equals(version)) throw new com.foodmate.shared.runtime.RuntimeException("RUNTIME_CONTRACT_INVALID", "unsupported runtime contract version");
    }
}
