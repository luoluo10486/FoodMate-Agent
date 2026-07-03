package com.foodmate.api.sse;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * SseEventEnvelope 的序列化测试。
 */
class SseEventEnvelopeTest {
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void serializesRunIdAsString() throws Exception {
        String json = objectMapper.writeValueAsString(
                SseEventEnvelope.of("run.created", 1912345678901234567L, Map.of("status", "queued"))
        );

        assertTrue(json.contains("\"event_type\":\"run.created\""));
        assertTrue(json.contains("\"run_id\":\"1912345678901234567\""));
    }
}
