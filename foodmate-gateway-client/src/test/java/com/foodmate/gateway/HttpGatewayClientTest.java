package com.foodmate.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodmate.shared.runtime.RunCommand;
import com.foodmate.shared.runtime.RuntimeException;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class HttpGatewayClientTest {
    @Test void dispatchPostsToGatewayAndMapsErrors() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/internal/runtime/runs:dispatch", e -> { e.sendResponseHeaders(429, 0); e.getResponseBody().close(); });
        server.start();
        try {
            var client = new HttpGatewayClient(URI.create("http://localhost:" + server.getAddress().getPort()), Duration.ofSeconds(2), HttpClient.newHttpClient(), new ObjectMapper());
            var command = new RunCommand("d1", "r1", "hello", Instant.now().plusSeconds(5), 1);
            assertEquals("RUNTIME_UNAVAILABLE", assertThrows(RuntimeException.class, () -> client.dispatch(command)).code());
        } finally { server.stop(0); }
    }
}
