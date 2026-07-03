package com.foodmate.shared.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;

/**
 * 将 Long ID 序列化为字符串。
 */
public class LongIdJsonSerializer extends JsonSerializer<Long> {
    @Override
    public void serialize(Long value, JsonGenerator generator, SerializerProvider serializers) throws IOException {
        if (value == null) {
            generator.writeNull();
            return;
        }
        generator.writeString(value.toString());
    }
}
