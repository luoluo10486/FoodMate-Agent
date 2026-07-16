package com.foodmate.gateway;

import com.foodmate.shared.runtime.CancelCommand;
import com.foodmate.shared.runtime.RunCommand;
import com.foodmate.shared.runtime.RuntimeException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class HttpGatewayClient implements GatewayClient {
    private final HttpClient client; private final ObjectMapper mapper; private final URI base; private final Duration timeout;
    public HttpGatewayClient(URI base, Duration timeout, HttpClient client, ObjectMapper mapper) { this.base = base; this.timeout = timeout; this.client = client; this.mapper = mapper; }
    public Response dispatch(RunCommand command) { return send("/internal/runtime/runs:dispatch", command, "dispatch"); }
    public Response cancel(CancelCommand command) { return send("/internal/runtime/runs:cancel", command, "cancel"); }
    private Response send(String path, Object body, String operation) {
        try {
            HttpRequest request = HttpRequest.newBuilder(base.resolve(path)).timeout(timeout).header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body))).build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300) return new Response(response.statusCode(), response.body());
            String code = response.statusCode() == 401 ? "RUNTIME_AUTH_INVALID" : response.statusCode() == 403 ? "RUNTIME_AUTH_FORBIDDEN" : response.statusCode() == 408 || response.statusCode() == 504 ? "RUNTIME_DEADLINE_EXCEEDED" : response.statusCode() == 429 || response.statusCode() >= 500 ? "RUNTIME_UNAVAILABLE" : "RUNTIME_CONTRACT_INVALID";
            throw new RuntimeException(code, operation + " failed: HTTP " + response.statusCode());
        } catch (java.net.http.HttpTimeoutException e) { throw new RuntimeException("RUNTIME_DEADLINE_EXCEEDED", e.getMessage()); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new RuntimeException("RUNTIME_UNAVAILABLE", e.getMessage()); }
        catch (IOException e) { throw new RuntimeException("RUNTIME_UNAVAILABLE", e.getMessage()); }
    }
}
