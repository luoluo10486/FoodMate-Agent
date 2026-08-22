package com.foodmate.shared.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class V1RunCommandTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void serializesAndDeserializesTypedRuntimeContext() throws Exception {
        V1RunCommand command =
                new V1RunCommand(
                        "v1",
                        "run-1",
                        "dispatch-1",
                        1,
                        "request-1",
                        "trace-1",
                        "sha256:request",
                        Instant.parse("2026-01-01T00:00:00Z"),
                        new V1RunCommand.V1Message(
                                "message-1",
                                "hello",
                                List.of(mapper.readTree("{\"kind\":\"text\"}"))),
                        new V1RunCommand.AuthorizedContext(
                                "session-1",
                                "Asia/Shanghai",
                                "zh-CN",
                                "v1",
                                List.of(
                                        new V1RunCommand.RecentMessage(
                                                "message-0", "user", "previous", 1)),
                                null,
                                List.of(
                                        new V1RunCommand.MemoryContext(
                                                "memory-1",
                                                "preference",
                                                "diet",
                                                "{\"value\":\"low-salt\"}",
                                                new BigDecimal("0.90"),
                                                "user")),
                                new V1RunCommand.SqlReadRequest("SELECT 1", "invocation-1", false),
                                "public_published"),
                        new V1RunCommand.RuntimeOptions(
                                "prompt-v1",
                                30,
                                true,
                                new V1RunCommand.BudgetSnapshot(
                                        30000,
                                        new BigDecimal("0.50"),
                                        2,
                                        1,
                                        1,
                                        30,
                                        12,
                                        30,
                                        120,
                                        30,
                                        86400,
                                        1,
                                        "m1-4-default"),
                                new V1RunCommand.ModelSnapshot(
                                        "agent_run",
                                        "chat",
                                        "route-1",
                                        "deterministic",
                                        "stub-chat-v1",
                                        null,
                                        null,
                                        "stub-price-v1",
                                        new BigDecimal("1.25"),
                                        new BigDecimal("2.50"),
                                        "stub-budget-v1",
                                        15000)),
                        null);

        String json = mapper.writeValueAsString(command);
        JsonNode root = mapper.readTree(json);
        assertEquals("session-1", root.path("authorized_context").path("session_id").asText());
        assertEquals(
                "invocation-1",
                root.path("authorized_context")
                        .path("sql_read_request")
                        .path("invocation_id")
                        .asText());
        assertEquals(
                0,
                new BigDecimal("0.50")
                        .compareTo(
                                root.path("runtime_options")
                                        .path("budget_snapshot")
                                        .path("max_cost_cny")
                                        .decimalValue()));
        assertEquals(
                "stub-price-v1",
                root.path("runtime_options").path("model_snapshot").path("price_version").asText());
        assertTrue(root.path("recovery_context").isMissingNode());

        V1RunCommand restored = mapper.readValue(json, V1RunCommand.class);
        assertEquals("message-1", restored.message().messageId());
        assertEquals(1, restored.authorizedContext().recentMessages().size());
        assertEquals(1, restored.runtimeOptions().budgetSnapshot().revision());
        assertEquals("route-1", restored.runtimeOptions().modelSnapshot().routeVersion());
    }
}
