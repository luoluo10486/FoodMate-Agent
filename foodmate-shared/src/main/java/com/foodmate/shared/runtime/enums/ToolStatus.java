package com.foodmate.shared.runtime.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

/** 工具注册状态。 */
public enum ToolStatus {
    ACTIVE("active"),
    DISABLED("disabled");

    private final String code;

    ToolStatus(String code) {
        this.code = code;
    }

    @JsonValue
    public String code() {
        return code;
    }

    @JsonCreator
    public static ToolStatus fromCode(String code) {
        return Arrays.stream(values())
                .filter(value -> value.code.equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow(
                        () -> new IllegalArgumentException("unsupported tool status: " + code));
    }
}
