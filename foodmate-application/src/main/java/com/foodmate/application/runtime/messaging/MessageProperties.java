package com.foodmate.application.runtime.messaging;

import java.util.List;
import java.util.Objects;

/** 不在应用契约中暴露通用 Map 的不可变传输元数据。 */
public final class MessageProperties {
    private final List<Property> values;

    private MessageProperties(List<Property> values) {
        this.values = List.copyOf(values);
    }

    public static MessageProperties empty() {
        return new MessageProperties(List.of());
    }

    public static MessageProperties of(Property... values) {
        return new MessageProperties(List.of(values));
    }

    public static MessageProperties copyOf(Iterable<Property> values) {
        return new MessageProperties(
                java.util.stream.StreamSupport.stream(values.spliterator(), false).toList());
    }

    public String get(String key) {
        for (Property property : values) {
            if (property.key().equals(key)) return property.value();
        }
        return null;
    }

    public List<Property> values() {
        return values;
    }

    public record Property(String key, String value) {
        public Property {
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("property key is required");
            }
            Objects.requireNonNull(value, "property value");
        }
    }
}
