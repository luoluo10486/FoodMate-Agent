package com.foodmate.api.controller;

import com.foodmate.application.runtime.RuntimeCheckpointRecoveryReconciler;
import com.foodmate.application.runtime.RuntimeGatewayService;
import com.foodmate.application.runtime.V1RuntimeEventService;
import com.foodmate.gateway.ServiceJwt;
import com.foodmate.shared.api.ApiResponse;
import com.foodmate.shared.runtime.CancelCommand;
import com.foodmate.shared.runtime.RunCommand;
import com.foodmate.shared.runtime.RunEvent;
import com.foodmate.shared.runtime.V1RunEvent;
import com.foodmate.shared.trace.TraceContextHolder;
import jakarta.validation.Valid;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RuntimeGatewayController {
    private final RuntimeGatewayService service;
    private final String contractVersion;
    private final boolean jwtEnabled;
    private final String javaPublicKey;
    private final String pythonPublicKey;
    private final V1RuntimeEventService v1Events;
    private final RuntimeCheckpointRecoveryReconciler recoveryReconciler;

    public RuntimeGatewayController(
            RuntimeGatewayService service,
            @Value("${foodmate.runtime.contract-version:v1}") String contractVersion,
            @Value("${foodmate.runtime.service-jwt.enabled:false}") boolean jwtEnabled,
            @Value("${foodmate.runtime.service-jwt.java-public-key:}") String javaPublicKey,
            @Value("${foodmate.runtime.service-jwt.python-public-key:}") String pythonPublicKey,
            ObjectProvider<V1RuntimeEventService> eventProvider,
            ObjectProvider<RuntimeCheckpointRecoveryReconciler> recoveryProvider) {
        this.service = service;
        this.contractVersion = contractVersion;
        this.jwtEnabled = jwtEnabled;
        this.javaPublicKey = javaPublicKey;
        this.pythonPublicKey = pythonPublicKey;
        this.v1Events = eventProvider.getIfAvailable();
        this.recoveryReconciler = recoveryProvider.getIfAvailable();
    }

    @PostMapping("/foodmate/internal/v1/agent-events")
    public ApiResponse<V1RuntimeEventService.EventResult> v1Event(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Contract-Version", required = false) String version,
            @Valid @RequestBody V1RunEvent event) {
        if (!contractVersion.equals(version))
            throw new com.foodmate.shared.runtime.RuntimeException(
                    "RUNTIME_CONTRACT_INVALID", "V1 contract header is required");
        authenticate(
                authorization, version, "foodmate-agent-runtime", pythonPublicKey, "agent:event");
        if (v1Events == null)
            throw new com.foodmate.shared.runtime.RuntimeException(
                    "RUNTIME_UNAVAILABLE", "V1 event service is not configured");
        return ApiResponse.success(v1Events.accept(event), TraceContextHolder.currentOrNew());
    }

    @PostMapping("/internal/runtime/runs:dispatch")
    public ApiResponse<RuntimeGatewayService.CommandResult> dispatch(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Contract-Version", required = false) String version,
            @Valid @RequestBody RunCommand command) {
        authenticate(
                authorization,
                version,
                "foodmate-control-plane",
                javaPublicKey,
                "runtime:dispatch");
        return ApiResponse.success(service.dispatch(command), TraceContextHolder.currentOrNew());
    }

    @PostMapping("/internal/runtime/runs:cancel")
    public ApiResponse<RuntimeGatewayService.CommandResult> cancel(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Contract-Version", required = false) String version,
            @Valid @RequestBody CancelCommand command) {
        authenticate(
                authorization, version, "foodmate-control-plane", javaPublicKey, "runtime:cancel");
        return ApiResponse.success(service.cancel(command), TraceContextHolder.currentOrNew());
    }

    @PostMapping("/internal/runtime/runs:events")
    public ApiResponse<RuntimeGatewayService.EventResult> event(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Contract-Version", required = false) String version,
            @Valid @RequestBody RunEvent event) {
        authenticate(
                authorization, version, "foodmate-agent-runtime", pythonPublicKey, "runtime:event");
        return ApiResponse.success(service.event(event), TraceContextHolder.currentOrNew());
    }

    /** Runtime startup notification used to trigger PostgreSQL-backed stale checkpoint recovery. */
    @PostMapping("/foodmate/internal/v1/runtime/recovered")
    public ApiResponse<RuntimeCheckpointRecoveryReconciler.TriggerResult> recovered(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Contract-Version", required = false) String version) {
        authenticate(
                authorization,
                version,
                "foodmate-agent-runtime",
                pythonPublicKey,
                "runtime:recovery");
        if (recoveryReconciler == null)
            throw new com.foodmate.shared.runtime.RuntimeException(
                    "RUNTIME_UNAVAILABLE", "recovery reconciler is not configured");
        return ApiResponse.success(
                recoveryReconciler.triggerStaleRecoveries(), TraceContextHolder.currentOrNew());
    }

    private void authenticate(
            String authorization, String version, String issuer, String publicKey, String scope) {
        if (version != null && !contractVersion.equals(version))
            throw new com.foodmate.shared.runtime.RuntimeException(
                    "RUNTIME_CONTRACT_INVALID", "unsupported runtime contract version");
        // Local development may disable service JWT; production keeps the strict path below.
        if (!jwtEnabled) return;
        if (!jwtEnabled
                || authorization == null
                || !authorization.startsWith("Bearer ")
                || publicKey.isBlank())
            throw new com.foodmate.shared.runtime.RuntimeException(
                    "RUNTIME_AUTH_INVALID", "service JWT is required");
        try {
            ServiceJwt.verify(
                    authorization.substring(7), publicKey, issuer, "foodmate-control-plane", scope);
        } catch (IllegalStateException exception) {
            throw new com.foodmate.shared.runtime.RuntimeException(
                    "RUNTIME_AUTH_INVALID", "invalid service JWT");
        }
    }
}
