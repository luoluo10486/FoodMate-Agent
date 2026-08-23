package com.foodmate.shared.runtime;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.time.Instant;
import java.util.Objects;

/** Legacy runtime command retained for compatibility with the string Run gateway. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record RunCommand(
        String dispatchId, String runId, String input, Instant deadlineAt, int attempt) {
    public RunCommand {
        require(dispatchId, "dispatchId");
        require(runId, "runId");
        require(input, "input");
        Objects.requireNonNull(deadlineAt, "deadlineAt");
        if (attempt < 1) throw new IllegalArgumentException("attempt must be positive");
    }

    private static void require(String value, String name) {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException(name + " must not be blank");
    }
}
