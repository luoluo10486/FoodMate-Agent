package com.foodmate.api.request.runtime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public record RuntimeProposalRequest(
        String proposalId,
        String runId,
        String proposalType,
        String schemaVersion,
        String toolName,
        String confirmationRef,
        JsonNode input,
        Payload payload) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
    public record Payload(String statement, String invocationId, String idempotencyKey) {}
}
