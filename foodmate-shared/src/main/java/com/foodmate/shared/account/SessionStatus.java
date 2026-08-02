package com.foodmate.shared.account;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

/** 会话状态，包括普通列表和软删除列表使用的状态。 */
public enum SessionStatus {
    ACTIVE("active"),
    ARCHIVED("archived"),
    DELETED("deleted");

    private final String code;

    SessionStatus(String code) {
        this.code = code;
    }

    @JsonValue
    public String code() {
        return code;
    }

    @JsonCreator
    public static SessionStatus fromCode(String code) {
        return Arrays.stream(values())
                .filter(value -> value.code.equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow(
                        () -> new IllegalArgumentException("unsupported session status: " + code));
    }
}
