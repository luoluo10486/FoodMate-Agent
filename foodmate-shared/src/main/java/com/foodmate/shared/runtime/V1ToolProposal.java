package com.foodmate.shared.runtime;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

/** V1 Python 到 Java 的工具提案消息。 */
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
    /** 工具提案的安全结构化载荷，不携带任何凭据。 */
    public record Payload(
            String statement,
            @JsonProperty("invocation_id") String invocationId,
            @JsonProperty("idempotency_key") String idempotencyKey) {}
}
