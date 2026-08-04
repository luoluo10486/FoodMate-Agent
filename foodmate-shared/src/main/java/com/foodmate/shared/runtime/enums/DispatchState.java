package com.foodmate.shared.runtime.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

/** Arbitration state of an agent-run dispatch. */
public enum DispatchState {
    ACTIVE("active"),
    SUPERSEDED("superseded"),
    EXPIRED("expired");

    private final String code;

    DispatchState(String code) {
        this.code = code;
    }

    @JsonValue
    public String code() {
        return code;
    }

    @JsonCreator
    public static DispatchState fromCode(String code) {
        return Arrays.stream(values())
                .filter(value -> value.code.equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow(
                        () -> new IllegalArgumentException("unsupported dispatch state: " + code));
    }
}
