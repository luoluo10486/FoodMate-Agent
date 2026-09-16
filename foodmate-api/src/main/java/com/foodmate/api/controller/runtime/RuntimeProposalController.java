package com.foodmate.api.controller.runtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodmate.api.request.runtime.RuntimeProposalRequest;
import com.foodmate.application.runtime.service.RuntimeProposalService;
import com.foodmate.application.runtime.service.ToolGatewayService;
import com.foodmate.shared.api.ApiResponse;
import com.foodmate.shared.runtime.V1ToolProposal;
import com.foodmate.shared.security.ServiceJwt;
import com.foodmate.shared.security.ServiceJwt.PublicKeyRing;
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
    private final RuntimeProposalService proposals;
    private final ObjectMapper mapper;
    private final String contractVersion;
    private final boolean jwtEnabled;
    private final PublicKeyRing pythonPublicKeys;

    public RuntimeProposalController(
            RuntimeProposalService proposals,
            ObjectMapper mapper,
            @Value("${foodmate.runtime.contract-version:v1}") String contractVersion,
            @Value("${foodmate.runtime.service-jwt.enabled:false}") boolean jwtEnabled,
            @Value("${foodmate.runtime.service-jwt.python-public-key:}") String pythonPublicKey,
            @Value("${foodmate.runtime.service-jwt.python-public-keys:}")
                    String pythonPublicKeyRing,
            @Value("${foodmate.runtime.service-jwt.python-kid:}") String pythonKid) {
        this.proposals = proposals;
        this.mapper = mapper.copy().findAndRegisterModules();
        this.contractVersion = contractVersion;
        this.jwtEnabled = jwtEnabled;
        this.pythonPublicKeys =
                ServiceJwt.parsePublicKeyRing(pythonPublicKeyRing, pythonKid, pythonPublicKey);
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
        V1ToolProposal proposal =
                new V1ToolProposal(
                        body.schemaVersion(),
                        body.proposalId(),
                        body.requestHash(),
                        body.runId(),
                        body.proposalType(),
                        body.requiresConfirmation(),
                        body.toolName(),
                        body.confirmationRef(),
                        body.input(),
                        payload == null
                                ? null
                                : new V1ToolProposal.Payload(
                                        payload.statement(),
                                        payload.invocationId(),
                                        payload.idempotencyKey()),
                        body.dispatchId(),
                        body.attempt());
        String rawPayload;
        try {
            rawPayload = mapper.writeValueAsString(body);
        } catch (JsonProcessingException exception) {
            throw new com.foodmate.shared.runtime.RuntimeException(
                    "RUNTIME_CONTRACT_INVALID", "Proposal 请求无法序列化");
        }
        return ApiResponse.success(
                proposals.execute(proposal, rawPayload), TraceContextHolder.currentOrNew());
    }

    private void authenticate(String authorization) {
        if (!jwtEnabled
                || authorization == null
                || !authorization.startsWith("Bearer ")
                || pythonPublicKeys.isEmpty()) {
            throw new com.foodmate.shared.runtime.RuntimeException(
                    "RUNTIME_AUTH_INVALID", "service JWT is required");
        }
        try {
            ServiceJwt.verify(
                    authorization.substring(7),
                    pythonPublicKeys,
                    "foodmate-agent-runtime",
                    "foodmate-control-plane",
                    "runtime:proposal");
        } catch (IllegalStateException exception) {
            throw new com.foodmate.shared.runtime.RuntimeException(
                    "RUNTIME_AUTH_INVALID", "invalid service JWT");
        }
    }
}
