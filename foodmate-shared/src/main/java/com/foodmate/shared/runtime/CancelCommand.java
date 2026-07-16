package com.foodmate.shared.runtime;

import java.time.Instant;
import java.util.Objects;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record CancelCommand(String cancelId, String runId, String reason, Instant deadlineAt) {
    public CancelCommand {
        require(cancelId, "cancelId"); require(runId, "runId");
        Objects.requireNonNull(deadlineAt, "deadlineAt");
    }
    private static void require(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
    }
}
