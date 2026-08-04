package com.foodmate.api.controller.runtime;

import com.foodmate.api.request.runtime.RuntimeProposalRequest;
import com.foodmate.application.runtime.service.ToolGatewayService;
import com.foodmate.shared.api.ApiResponse;
import com.foodmate.shared.security.ServiceJwt;
import com.foodmate.shared.trace.TraceContextHolder;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/** Python Proposal 的 Java 入口；生产环境由 RocketMQ consumer 调用同一应用服务。 */
@RestController
public class RuntimeProposalController {
    private final ToolGatewayService gateway;
    private final String contractVersion;
    private final boolean jwtEnabled;
    private final String pythonPublicKey;

    public RuntimeProposalController(
            ToolGatewayService gateway,
            @Value("${foodmate.runtime.contract-version:v1}") String contractVersion,
            @Value("${foodmate.runtime.service-jwt.enabled:false}") boolean jwtEnabled,
            @Value("${foodmate.runtime.service-jwt.python-public-key:}") String pythonPublicKey) {
        this.gateway = gateway;
        this.contractVersion = contractVersion;
        this.jwtEnabled = jwtEnabled;
        this.pythonPublicKey = pythonPublicKey;
    }

    @PostMapping("/foodmate/internal/v1/proposals")
    public ApiResponse<ToolGatewayService.ProposalResult> proposal(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestHeader(value = "X-Contract-Version", required = false) String version,
            @Valid @RequestBody RuntimeProposalRequest body) {
        if (!contractVersion.equals(version))
            throw new com.foodmate.shared.runtime.RuntimeException(
                    "RUNTIME_CONTRACT_INVALID", "V1 contract header is required");
        authenticate(authorization);
        RuntimeProposalRequest.Payload payload = body.payload();
        return ApiResponse.success(
                gateway.execute(
                        new ToolGatewayService.ProposalCommand(
                                body.proposalId(),
                                body.runId(),
                                body.proposalType(),
                                body.schemaVersion(),
                                payload == null
                                        ? null
                                        : new ToolGatewayService.ProposalPayload(
                                                payload.statement(), payload.invocationId()))),
                TraceContextHolder.currentOrNew());
    }

    private void authenticate(String authorization) {
        if (!jwtEnabled
                || authorization == null
                || !authorization.startsWith("Bearer ")
                || pythonPublicKey.isBlank()) {
            throw new com.foodmate.shared.runtime.RuntimeException(
                    "RUNTIME_AUTH_INVALID", "service JWT is required");
        }
        try {
            ServiceJwt.verify(
                    authorization.substring(7),
                    pythonPublicKey,
                    "foodmate-agent-runtime",
                    "foodmate-control-plane",
                    "runtime:proposal");
        } catch (IllegalStateException exception) {
            throw new com.foodmate.shared.runtime.RuntimeException(
                    "RUNTIME_AUTH_INVALID", "invalid service JWT");
        }
    }
}
