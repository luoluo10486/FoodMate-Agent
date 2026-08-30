package com.foodmate.infrastructure.client.runtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodmate.application.knowledge.port.out.KnowledgeSearchPort;
import com.foodmate.shared.runtime.RuntimeException;
import com.foodmate.shared.security.ServiceJwt;
import com.foodmate.shared.trace.TraceContext;
import com.foodmate.shared.trace.TraceContextHeaders;
import com.foodmate.shared.trace.TraceContextHolder;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Runtime 负责的公共知识索引 HTTP 适配器。 */
public final class V1HttpKnowledgeSearchClient implements KnowledgeSearchPort {
    private static final String PUBLIC_SCOPE = "public_published";

    private final URI base;
    private final Duration timeout;
    private final HttpClient client;
    private final ObjectMapper mapper;
    private final String privateKey;
    private final String kid;
    private final String contractVersion;
    private final boolean jwtEnabled;

    public V1HttpKnowledgeSearchClient(
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
    public SearchResult search(String query, String knowledgeScope) {
        if (!PUBLIC_SCOPE.equals(knowledgeScope))
            throw new RuntimeException("RAG_SCOPE_DENIED", "only public knowledge is searchable");
        try {
            TraceContext context = TraceContextHolder.currentOrNew();
            HttpRequest.Builder builder =
                    HttpRequest.newBuilder(base.resolve("/foodmate/internal/v1/knowledge/search"))
                            .timeout(timeout)
                            .header("Content-Type", "application/json")
                            .header("X-Contract-Version", contractVersion)
                            .header("X-Request-Id", context.requestId())
                            .header("X-Trace-Id", context.traceId())
                            .header("traceparent", TraceContextHeaders.traceparent(context));
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
                                        "runtime:knowledge-search",
                                        kid,
                                        60));
            }
            HttpRequest request =
                    builder.POST(
                                    HttpRequest.BodyPublishers.ofString(
                                            mapper.createObjectNode()
                                                    .put("query", query)
                                                    .put("knowledge_scope", PUBLIC_SCOPE)
                                                    .toString()))
                            .build();
            HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300)
                throw new RuntimeException(
                        response.statusCode() == 401
                                ? "RUNTIME_AUTH_INVALID"
                                : response.statusCode() == 403
                                        ? "RAG_SCOPE_DENIED"
                                        : response.statusCode() >= 500
                                                ? "RAG_UNAVAILABLE"
                                                : "RAG_QUERY_INVALID",
                        "knowledge search failed: HTTP " + response.statusCode());
            return parse(response.body());
        } catch (java.net.http.HttpTimeoutException exception) {
            throw new RuntimeException("RUNTIME_DEADLINE_EXCEEDED", exception.getMessage());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("RAG_UNAVAILABLE", exception.getMessage());
        } catch (IOException exception) {
            throw new RuntimeException("RAG_UNAVAILABLE", exception.getMessage());
        }
    }

    private SearchResult parse(String body) throws IOException {
        JsonNode root = mapper.readTree(body);
        if (!PUBLIC_SCOPE.equals(root.path("knowledge_scope").asText()))
            throw new RuntimeException("RAG_SCOPE_DENIED", "Runtime returned an invalid scope");
        List<Citation> citations = new ArrayList<>();
        for (JsonNode node : root.withArray("citations")) {
            long documentId = parseDocumentId(node.path("document_id").asText());
            String citationId = required(node, "citation_id");
            citations.add(
                    new Citation(
                            documentId,
                            citationId,
                            required(node, "title"),
                            required(node, "version"),
                            node.path("section_path").asText(""),
                            required(node, "snippet")));
            if (citations.size() == 4) break;
        }
        return new SearchResult(List.copyOf(citations));
    }

    private static long parseDocumentId(String value) {
        try {
            long id = Long.parseLong(value);
            if (id > 0) return id;
        } catch (NumberFormatException ignored) {
            // Convert malformed Runtime data into a stable contract error below.
        }
        throw new RuntimeException(
                "RUNTIME_CONTRACT_INVALID", "Runtime returned an invalid document id");
    }

    private static String required(JsonNode node, String field) {
        String value = node.path(field).asText("");
        if (value.isBlank())
            throw new RuntimeException(
                    "RUNTIME_CONTRACT_INVALID", "Runtime returned an incomplete citation");
        return value;
    }
}
