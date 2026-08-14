package com.foodmate.shared.runtime;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

/** V1 Python -> Java tool proposal message. */
public record V1ToolProposal(
        @JsonProperty("schema_version") String schemaVersion,
        @JsonProperty("proposal_id") String proposalId,
        @JsonProperty("request_hash") String requestHash,
        @JsonProperty("run_id") String runId,
        @JsonProperty("proposal_type") String proposalType,
        @JsonProperty("requires_confirmation") boolean requiresConfirmation,
        @JsonProperty("tool_name") String toolName,
        @JsonProperty("confirmation_ref") String confirmationRef,
        JsonNode input,
        Payload payload) {
    public record Payload(
            String statement,
            @JsonProperty("invocation_id") String invocationId,
            @JsonProperty("idempotency_key") String idempotencyKey) {}
}
