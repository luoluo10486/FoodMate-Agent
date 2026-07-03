package com.foodmate.shared.json;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import org.junit.jupiter.api.Test;

/**
 * LongIdJsonSerializer 的序列化测试。
 */
class LongIdJsonSerializerTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void serializesLongIdAsString() throws Exception {
        String json = objectMapper.writeValueAsString(new SampleDto(1912345678901234567L));

        assertEquals("{\"id\":\"1912345678901234567\"}", json);
    }

    /**
     * 序列化测试用 DTO。
     */
    private record SampleDto(@JsonSerialize(using = LongIdJsonSerializer.class) Long id) {
    }
}
