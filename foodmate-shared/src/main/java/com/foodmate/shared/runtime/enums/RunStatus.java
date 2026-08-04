package com.foodmate.shared.runtime.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;

/** AgentRun lifecycle status persisted by the control plane. */
public enum RunStatus {
    QUEUED("queued", false),
    ROUTED("routed", false),
    WAITING_USER("waiting_user", false),
    PLANNING("planning", false),
    RETRIEVING("retrieving", false),
    EXECUTING("executing", false),
    VALIDATING("validating", false),
    COMPLETED("completed", true),
    FAILED("failed", true),
    CANCELLED("cancelled", true),
    SUPERSEDED("superseded", true);

    private final String code;
    private final boolean terminal;

    RunStatus(String code, boolean terminal) {
        this.code = code;
        this.terminal = terminal;
    }

    @JsonValue
    public String code() {
        return code;
    }

    public boolean isTerminal() {
        return terminal;
    }

    @JsonCreator
    public static RunStatus fromCode(String code) {
        return Arrays.stream(values())
                .filter(value -> value.code.equalsIgnoreCase(code))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unsupported run status: " + code));
    }
}
