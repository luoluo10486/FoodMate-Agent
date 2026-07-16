package com.foodmate.shared.runtime;

import java.time.Instant;
import java.util.Objects;

public record RunCommand(String dispatchId, String runId, String input, Instant deadlineAt, int attempt) {
    public RunCommand {
        require(dispatchId, "dispatchId");
        require(runId, "runId");
        require(input, "input");
        Objects.requireNonNull(deadlineAt, "deadlineAt");
        if (attempt < 1) throw new IllegalArgumentException("attempt must be positive");
    }
    private static void require(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
    }
}
