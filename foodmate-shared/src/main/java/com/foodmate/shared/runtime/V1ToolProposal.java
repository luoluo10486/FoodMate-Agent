package com.foodmate.shared.runtime;

import com.fasterxml.jackson.annotation.JsonProperty;

/** V1 Python -> Java tool proposal message. */
public record V1ToolProposal(
        @JsonProperty("schema_version") String schemaVersion,
        @JsonProperty("proposal_id") String proposalId,
        @JsonProperty("request_hash") String requestHash,
        @JsonProperty("run_id") String runId,
        @JsonProperty("proposal_type") String proposalType,
        @JsonProperty("requires_confirmation") boolean requiresConfirmation,
        Payload payload) {
    public record Payload(String statement, @JsonProperty("invocation_id") String invocationId) {}
}
