package com.foodmate.gateway;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodmate.shared.runtime.RuntimeException;
import com.foodmate.shared.runtime.V1CancelCommand;
import com.foodmate.shared.runtime.V1RunCommand;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.UUID;

/** Java -> Python 的 V1 传输客户端，不重新拼装已持久化的 outbox payload。 */
public final class V1HttpRuntimeClient implements V1RuntimeClient {
    private final URI base;
    private final Duration timeout;
    private final HttpClient client;
    private final ObjectMapper mapper;
    private final String privateKey;
    private final String kid;
    private final String contractVersion;
    private final boolean jwtEnabled;

    public V1HttpRuntimeClient(
            URI base,
            Duration timeout,
            HttpClient client,
            ObjectMapper mapper,
            String privateKey,
            String kid,
            String contractVersion) {
        this(base, timeout, client, mapper, privateKey, kid, contractVersion, true);
    }

    public V1HttpRuntimeClient(
            URI base,
            Duration timeout,
            HttpClient client,
            ObjectMapper mapper,
            String privateKey,
            String kid,
            String contractVersion,
            boolean jwtEnabled) {
        this.base = base;
        this.timeout = timeout;
        this.client = client;
        this.mapper = mapper.findAndRegisterModules();
        this.privateKey = privateKey == null ? "" : privateKey;
        this.kid = kid == null ? "" : kid;
        this.contractVersion =
                contractVersion == null || contractVersion.isBlank() ? "v1" : contractVersion;
        this.jwtEnabled = jwtEnabled;
    }

    @Override
    public Response dispatch(V1RunCommand command) {
        return send("/foodmate/internal/v1/runs", command, "runtime:dispatch");
    }

    @Override
    public Response cancel(V1CancelCommand command) {
        return send(
                "/foodmate/internal/v1/runs/" + command.runId() + "/cancel",
                command,
                "runtime:cancel");
    }

    private Response send(String path, Object body, String scope) {
        try {
            HttpRequest.Builder builder =
                    HttpRequest.newBuilder(base.resolve(path))
                            .timeout(timeout)
                            .header("Content-Type", "application/json")
                            .header("X-Contract-Version", contractVersion)
                            .header(
                                    "X-Request-Id",
                                    "req_http_" + UUID.randomUUID().toString().replace("-", ""))
                            .header(
                                    "traceparent",
                                    "00-"
                                            + UUID.randomUUID().toString().replace("-", "")
                                            + "-"
                                            + UUID.randomUUID()
                                                    .toString()
                                                    .replace("-", "")
                                                    .substring(0, 16)
                                            + "-01");
            if (jwtEnabled) {
                if (privateKey.isBlank() || kid.isBlank())
                    throw new RuntimeException(
                            "RUNTIME_AUTH_INVALID", "Java service JWT is not configured");
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
            }
            HttpRequest request =
                    builder.POST(
                                    HttpRequest.BodyPublishers.ofString(
                                            mapper.writeValueAsString(body)))
                            .build();
            HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300)
                return new Response(response.statusCode(), response.body());
            String code =
                    response.statusCode() >= 500
                                    || response.statusCode() == 408
                                    || response.statusCode() == 504
                            ? "RUNTIME_UNAVAILABLE"
                            : response.statusCode() == 409
                                    ? "RUNTIME_STATE_CONFLICT"
                                    : "RUNTIME_CONTRACT_INVALID";
            throw new RuntimeException(
                    code, "runtime request failed: HTTP " + response.statusCode());
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
