package com.foodmate.infrastructure.client.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodmate.application.runtime.port.out.RuntimeGatewayPort;
import com.foodmate.shared.runtime.CancelCommand;
import com.foodmate.shared.runtime.RunCommand;
import com.foodmate.shared.runtime.RuntimeException;
import com.foodmate.shared.security.ServiceJwt;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/** HTTP adapter for the runtime gateway port. */
public final class HttpGatewayClient implements RuntimeGatewayPort {
    private final HttpClient client;
    private final ObjectMapper mapper;
    private final URI base;
    private final Duration timeout;
    private final String privateKey;
    private final String kid;
    private final String contractVersion;

    public HttpGatewayClient(URI base, Duration timeout, HttpClient client, ObjectMapper mapper) {
        this(base, timeout, client, mapper, "", "", "v1");
    }

    public HttpGatewayClient(
            URI base,
            Duration timeout,
            HttpClient client,
            ObjectMapper mapper,
            String privateKey,
            String kid,
            String contractVersion) {
        this.base = base;
        this.timeout = timeout;
        this.client = client;
        this.mapper = mapper;
        this.privateKey = privateKey == null ? "" : privateKey;
        this.kid = kid == null ? "" : kid;
        this.contractVersion =
                contractVersion == null || contractVersion.isBlank() ? "v1" : contractVersion;
    }

    @Override
    public RuntimeGatewayPort.Response dispatch(RunCommand command) {
        return send("/internal/runtime/runs:dispatch", command, "dispatch");
    }

    @Override
    public RuntimeGatewayPort.Response cancel(CancelCommand command) {
        return send("/internal/runtime/runs:cancel", command, "cancel");
    }

    private RuntimeGatewayPort.Response send(String path, Object body, String operation) {
        try {
            HttpRequest.Builder builder =
                    HttpRequest.newBuilder(base.resolve(path))
                            .timeout(timeout)
                            .header("Content-Type", "application/json")
                            .header("X-Contract-Version", contractVersion);
            if (privateKey.isBlank() || kid.isBlank())
                throw new RuntimeException(
                        "RUNTIME_UNAVAILABLE", "runtime service JWT is not configured");
            String scope = "dispatch".equals(operation) ? "runtime:dispatch" : "runtime:cancel";
            builder.header(
                    "Authorization",
                    "Bearer "
                            + ServiceJwt.sign(
                                    privateKey,
                                    "foodmate-control-plane",
                                    "foodmate-agent-runtime",
                                    scope,
                                    kid,
                                    60));
            HttpRequest request =
                    builder.POST(
                                    HttpRequest.BodyPublishers.ofString(
                                            mapper.writeValueAsString(body)))
                            .build();
            HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300)
                return new RuntimeGatewayPort.Response(response.statusCode(), response.body());
            String code =
                    response.statusCode() == 401
                            ? "RUNTIME_AUTH_INVALID"
                            : response.statusCode() == 403
                                    ? "RUNTIME_AUTH_FORBIDDEN"
                                    : response.statusCode() == 408 || response.statusCode() == 504
                                            ? "RUNTIME_DEADLINE_EXCEEDED"
                                            : response.statusCode() == 429
                                                            || response.statusCode() >= 500
                                                    ? "RUNTIME_UNAVAILABLE"
                                                    : "RUNTIME_CONTRACT_INVALID";
            throw new RuntimeException(code, operation + " failed: HTTP " + response.statusCode());
        } catch (java.net.http.HttpTimeoutException exception) {
            throw new RuntimeException("RUNTIME_DEADLINE_EXCEEDED", exception.getMessage());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("RUNTIME_UNAVAILABLE", exception.getMessage());
        } catch (IOException exception) {
            throw new RuntimeException("RUNTIME_UNAVAILABLE", exception.getMessage());
        }
    }
}
