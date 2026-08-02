package com.foodmate.shared.account;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

/** 会话运行模式。 */
public enum SessionMode {
    AGENT("agent"),
    CHAT("chat");

    private final String code;

    SessionMode(String code) {
        this.code = code;
    }

    @JsonValue
    public String code() {
        return code;
    }

    @JsonCreator
    public static SessionMode fromCode(String code) {
        return Arrays.stream(values())
                .filter(value -> value.code.equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow(
                        () -> new IllegalArgumentException("unsupported session mode: " + code));
    }
}
